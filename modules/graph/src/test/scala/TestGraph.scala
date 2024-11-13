package at.ac.oeaw.imba.gerlich.gerlib.graph

import scala.reflect.ClassTag
import scalax.collection.edges.UnDiEdge
import scalax.collection.generator.*
import scalax.collection.immutable.Graph

import cats.*
import cats.syntax.all.*
import cats.laws.discipline.MonoidKTests
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import org.scalacheck.*
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should
import org.scalatest.prop.Configuration.PropertyCheckConfiguration
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/** Test the functionality of the graph module. */
class TestGraph
    extends AnyFunSuiteLike,
      FunSuiteDiscipline,
      ScalaCheckPropertyChecks,
      should.Matchers:

  private val maxOrder: Int = 20

  def genRandomGraphMetrics[N: Arbitrary]: Gen[GraphGen.Metrics[N]] =
    Gen.choose(1, maxOrder).map { n => // RandomGraph throws exception for order-0.
      new GraphGen.Metrics[N]:
        override def order: Int = n
        override def nodeDegrees: NodeDegreeRange =
          // Don't let graph order approach vertex degreer, else
          // too many edge add tries will fail and the generator will stop.
          NodeDegreeRange(0, math.ceil(n / 3.0).toInt)
        override def nodeGen: Gen[N] = Arbitrary.arbitrary[N]
        override def connected: Boolean = false
    }

  given arbitrarySimplestGraph[N: Arbitrary: ClassTag]: Arbitrary[SimplestGraph[N]] =
    def genEmpty: Gen[SimplestGraph[N]] = Graph.empty
    def genNonEmpty: Gen[SimplestGraph[N]] =
      genRandomGraphMetrics.flatMap(
        GraphGen.fromMetrics[N, UnDiEdge[N], Graph](Graph, _, Set(UnDiEdge)).apply
      )
    Arbitrary { Gen.frequency(1 -> genEmpty, (maxOrder - 1) -> genNonEmpty) }

  given eqSimplestGraphByOuter[N: Eq]: Eq[SimplestGraph[N]] =
    Eq.by: g =>
      (g.nodes.toOuter, g.edges.toOuter)

  // needed since we're in AnyFunSuiteLike land
  override implicit val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 100)

  // Check laws for graphs monoid defined by outer-node union and outer-edge union.
  checkAll(
    "graph.SimplestGraph.MonoidKLaws",
    MonoidKTests[SimplestGraph](using monoidKForSimplestGraphByOuterElements).monoidK[Int]
  )

  test("graph.buildSimpleGraph yields graph with correct node set."):
    forAll { (adj: Map[Int, Set[Int]]) =>
      val expNodes = adj.keySet | adj.values.toList.combineAll
      val obsNodes =
        val g = buildSimpleGraph(adj)
        g.nodes.toOuter
      obsNodes shouldEqual expNodes
    }
end TestGraph
