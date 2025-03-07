package at.ac.oeaw.imba.gerlich.gerlib.geometry

import scala.math.{pow, sqrt}
import cats.*
import cats.data.Validated
import cats.syntax.all.*

import at.ac.oeaw.imba.gerlich.gerlib.numeric.*

/** Something that can compare two {@code A} values w.r.t. threshold value of type {@code T}
  */
trait ProximityComparable[A]:
  /** Are the two {@code A} values within threshold {@code T} of each other? */
  def proximal: (A, A) => Boolean
end ProximityComparable

/** Helpers for working with proximity comparisons */
object ProximityComparable:
  extension [A](a1: A)(using ev: ProximityComparable[A])
    infix def proximal(a2: A): Boolean = ev.proximal(a1, a2)

  given contravariantForProximityComparable: Contravariant[ProximityComparable] =
    new Contravariant[ProximityComparable]:
      override def contramap[A, B](fa: ProximityComparable[A])(f: B => A) =
        new ProximityComparable[B]:
          override def proximal = (b1, b2) => fa.proximal(f(b1), f(b2))
end ProximityComparable

/** A threshold on distances, which should be nonnegative, to be semantically contextualised by the
  * subtype
  */
sealed trait DistanceThreshold:
  def get: NonnegativeReal

/** Helpers for working with distance thresholds */
object DistanceThreshold:
  given showForDistanceThreshold: Show[DistanceThreshold] = Show.show { (t: DistanceThreshold) =>
    val typeName = t match
    case _: EuclideanDistance.Threshold            => "Euclidean"
    case _: PiecewiseDistance.ConjunctiveThreshold => "Conjunctive"
    s"${typeName}Threshold(${t.get})"
  }

  /** Define a proximity comparison for 3D points values.
    *
    * @param threshold
    *   The distance beneath which to consider a given pair of points as proximal
    * @return
    *   An instance with which to check pairs of points for proximity, according to the given
    *   threshold value ('think': decision boundary)
    * @see
    *   [[at.ac.oeaw.imba.gerlich.gerlib.geometry.Point3D]]
    */
  def defineProximityPointwise(
      threshold: DistanceThreshold
  ): ProximityComparable[Point3D[Double]] = threshold match
  case t: EuclideanDistance.Threshold =>
    new ProximityComparable[Point3D[Double]]:
      override def proximal = (a, b) =>
        val d = EuclideanDistance.between(a, b)
        if d.isInfinite then
          throw new EuclideanDistance.OverflowException(
            s"Cannot compute finite distance between $a and $b"
          )
        d `lessThan` t
  case t: PiecewiseDistance.ConjunctiveThreshold =>
    new ProximityComparable[Point3D[Double]]:
      override def proximal = PiecewiseDistance.within(t)

  /** Define a proximity comparison for values of arbitrary type, according to given threshold and
    * how to extract a 3D point value.
    *
    * @tparam A
    *   The type of value from which a 3D point will be extracted for purpose of proximity check /
    *   comparison
    * @param threshold
    *   The distance beneath which to consider a given pair of points as proximal
    * @return
    *   An instance with which to check pairs of values for proximity, according to the given
    *   threshold value ('think': decision boundary), and how to get a 3D point from a value of type
    *   `A`
    * @see
    *   [[at.ac.oeaw.imba.gerlich.gerlib.geometry.Point3D]]
    */
  def defineProximityPointwise[A](
      threshold: DistanceThreshold
  ): (A => Point3D[Double]) => ProximityComparable[A] =
    defineProximityPointwise(threshold).contramap
end DistanceThreshold

/** Piecewise / by-component distance, as absolute differences
  *
  * @param x
  *   The x-component of the absolute difference between two points' coordinatess
  * @param y
  *   The y-component of the absolute difference between two points' coordinates
  * @param z
  *   The z-component of the absolute difference between two points' coordinates
  */
final class PiecewiseDistance private (
    x: NonnegativeReal,
    y: NonnegativeReal,
    z: NonnegativeReal
):
  def getX: NonnegativeReal = x
  def getY: NonnegativeReal = y
  def getZ: NonnegativeReal = z

/** Helpers for working with distances in by-component / piecewise fashion */
object PiecewiseDistance:

  /** Distance threshold in which predicate comparing values to this threshold operates
    * conjunctively over components
    */
  final case class ConjunctiveThreshold(get: NonnegativeReal) extends DistanceThreshold

  /** Compute the piecewise / component-wise distance between the given points.
    *
    * @param a
    *   One point
    * @param b
    *   The other point
    * @return
    *   A wrapper with access to the (absolute) difference between each component / dimension of the
    *   two given points' coordinates
    * @throws java.lang.ArithmeticException
    *   if taking any absolute difference fails to refine as nonnegative
    */
  def between(a: Point3D[Double], b: Point3D[Double]): PiecewiseDistance =
    val xNel =
      NonnegativeReal.either((a.x.value - b.x.value).abs).toValidatedNel
    val yNel =
      NonnegativeReal.either((a.y.value - b.y.value).abs).toValidatedNel
    val zNel =
      NonnegativeReal.either((a.z.value - b.z.value).abs).toValidatedNel
    (xNel, yNel, zNel).tupled match
    case Validated.Valid((delX, delY, delZ)) =>
      PiecewiseDistance(x = delX, y = delY, z = delZ)
    case Validated.Invalid(es) =>
      throw new ArithmeticException:
        s"Computing distance between point $a and point $b yielded ${es.length} error(s): ${es.mkString_("; ")}"

  /** Are points closer than given threshold along each axis? */
  def within(
      threshold: ConjunctiveThreshold
  )(a: Point3D[Double], b: Point3D[Double]): Boolean =
    val d = between(a, b)
    d.getX < threshold.get && d.getY < threshold.get && d.getZ < threshold.get
end PiecewiseDistance

/** Semantic wrapper to denote that a nonnegative real number represents a Euclidean distance
  */
final case class EuclideanDistance private (get: NonnegativeReal):
  final def lessThan(t: EuclideanDistance.Threshold): Boolean = get < t.get
  final def greaterThan = !lessThan(_: EuclideanDistance.Threshold)
  final def equalTo(t: EuclideanDistance.Threshold) =
    !lessThan(t) && !greaterThan(t)
  final def lteq(t: EuclideanDistance.Threshold) = lessThan(t) || equalTo(t)
  final def gteq(t: EuclideanDistance.Threshold) = greaterThan(t) || equalTo(t)
  final def isFinite = get.isFinite
  final def isInfinite = !isFinite
end EuclideanDistance

/** Helpers for working with Euclidean distances */
object EuclideanDistance:
  import at.ac.oeaw.imba.gerlich.gerlib.numeric.instances.nonnegativeReal.given // for Order

  /** Order distance by the wrapped value. */
  given Order[EuclideanDistance] = Order.by(_.get)

  /** When something goes wrong with a distance computation or comparison */
  final case class OverflowException(message: String) extends Exception(message)

  /** Comparison basis for Euclidean distance between points */
  final case class Threshold(get: NonnegativeReal) extends DistanceThreshold

  // TODO: account for infinity/null-numeric cases.
  def between[C: Numeric](a: Point3D[C], b: Point3D[C]): EuclideanDistance =
    (a, b) match
    case (Point3D(x1, y1, z1), Point3D(x2, y2, z2)) =>
      val ss = sumOfSquares(
        List(
          x1.value -> x2.value,
          y1.value -> y2.value,
          z1.value -> z2.value
        )
      )
      val d = NonnegativeReal.unsafe(sqrt(ss))
      new EuclideanDistance(d)

  /** Use a lens of a 3D point from arbitrary type {@code A} to compute distance between {@code A}
    * values.
    */
  def between[A, C: Numeric](p: A => Point3D[C])(a1: A, a2: A): EuclideanDistance =
    between(p(a1), p(a2))

  private def sumOfSquares[C: Numeric](cs: Iterable[(C, C)]): Double =
    import scala.math.Numeric.Implicits.infixNumericOps
    cs.foldLeft(0.0) { case (acc, (a, b)) => acc + pow((a - b).toDouble, 2) }
end EuclideanDistance
