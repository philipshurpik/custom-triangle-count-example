/**
Copy paste from spark
  */

import scala.reflect.ClassTag
import org.apache.spark.graphx._
import org.apache.spark.util.collection.{OpenHashSet}


object CustomTriangleCount {

  type VertexSet = OpenHashSet[VertexId]

  def run[VD: ClassTag, ED: ClassTag](graph: Graph[VD, ED]): Graph[Int, ED] = {
    // Transform the edge data something cheap to shuffle and then canonicalize
    val canonicalGraph = graph.mapEdges(e => true).removeSelfEdges().convertToCanonicalEdges()
    // Get the triangle counts
    val counters = runPreCanonicalized(canonicalGraph).vertices
    // Join them bath with the original graph
    graph.outerJoinVertices(counters) { (vid, _, optCounter: Option[Int]) =>
      optCounter.getOrElse(0)
    }
  }


  def runPreCanonicalized[VD: ClassTag, ED: ClassTag](graph: Graph[VD, ED]): Graph[Int, ED] = {
    // Construct set representations of the neighborhoods
    val nbrSets: VertexRDD[VertexSet] =
      graph.collectNeighborIds(EdgeDirection.Either).mapValues { (vid, nbrs) =>
        val set = new VertexSet(nbrs.length)
        var i = 0
        while (i < nbrs.length) {
          // prevent self cycle
          if (nbrs(i) != vid) {
            set.add(nbrs(i))
          }
          i += 1
        }
        set
      }

    // join the sets with the graph
    val setGraph: Graph[VertexSet, ED] = graph.outerJoinVertices(nbrSets) {
      (vid, _, optSet) => optSet.getOrElse(null)
    }

    // Edge function computes intersection of smaller vertex with larger vertex
    def edgeFunc(ctx: EdgeContext[VertexSet, ED, Int]) {
      val (smallSet, largeSet) = if (ctx.srcAttr.size < ctx.dstAttr.size) {
        (ctx.srcAttr, ctx.dstAttr)
      } else {
        (ctx.dstAttr, ctx.srcAttr)
      }
      val iter = smallSet.iterator
      var counter: Int = 0
      while (iter.hasNext) {
        val vid = iter.next()
        if (vid != ctx.srcId && vid != ctx.dstId && largeSet.contains(vid)) {
          counter += 1
        }
      }
      ctx.sendToSrc(counter)
      ctx.sendToDst(counter)
    }

    // compute the intersection along edges
    val counters: VertexRDD[Int] = setGraph.aggregateMessages(edgeFunc, _ + _)
    // Merge counters with the graph and divide by two since each triangle is counted twice
    graph.outerJoinVertices(counters) { (_, _, optCounter: Option[Int]) =>
      val dblCount = optCounter.getOrElse(0)
      // This algorithm double counts each triangle so the final count should be even
      require(dblCount % 2 == 0, "Triangle count resulted in an invalid number of triangles.")
      dblCount / 2
    }
  }
}
