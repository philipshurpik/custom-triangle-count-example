import org.apache.log4j.{Level, Logger}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.graphx.{GraphLoader, PartitionStrategy}

object Main {

  def main(args: Array[String]) {
    Logger.getRootLogger.setLevel(Level.ERROR)

    val sc = new SparkContext(new SparkConf()
      .setMaster("local")
      .setAppName("Vertex Count"))

    val graph = GraphLoader.edgeListFile(sc, "data/followers.txt", true)
      .partitionBy(PartitionStrategy.RandomVertexCut)

    // val triCounts = graph.triangleCount().vertices
    val triCounts = CustomTriangleCount.run(graph).vertices

    println("Sum of vertices that are inside triangles:", triCounts.values.sum())
    println("Sum of unique triangles:", triCounts.values.sum() / 3)
    sc.stop()
  }
}