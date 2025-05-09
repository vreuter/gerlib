package at.ac.oeaw.imba.gerlich.gerlib

import cats.*
import cats.derived.*
import cats.syntax.all.*

import io.github.iltotore.iron.{:|, refineEither}
import io.github.iltotore.iron.cats.given
import io.github.iltotore.iron.constraint.any.{Not, StrictEqual}
import io.github.iltotore.iron.constraint.char.{Digit, Letter}
import io.github.iltotore.iron.constraint.collection.{Empty, ForAll}
import io.github.iltotore.iron.constraint.string.Match

import at.ac.oeaw.imba.gerlich.gerlib.numeric.*
import at.ac.oeaw.imba.gerlich.gerlib.numeric.instances.nonnegativeInt.given

/** Tools and types related to imaging */
package object imaging:
  private type Parser[Result] = String => Either[String, Result]

  /** A field of view is a value of one of a relatively small set of types. */
  sealed trait FieldOfViewLike

  /** Helpers for working with FOV-like values */
  object FieldOfViewLike:
    def parse: Parser[FieldOfViewLike] =
      s =>
        FieldOfView.parse(s).leftFlatMap { e1 =>
          PositionName.parse(s).leftMap { e2 =>
            s"Could not parse FOV-like. Message 1: $e1. Message 2: $e2"
          }
        }

  /** Type wrapper around 0-based index of field of view (FOV) */
  final case class FieldOfView(private[imaging] get: NonnegativeInt) extends FieldOfViewLike
      derives Order:
    def getRawValue: NonnegativeInt = get

  /** Helpers for working with fields of view */
  object FieldOfView:
    /** Safely try to lift ordinary integer into field of view wrapper. */
    def fromInt: Int => Either[String, FieldOfView] =
      NonnegativeInt.either.map(_.map(FieldOfView.apply))

    /** Wrap the given value as a field of view, if it's valid as one. */
    def parse: Parser[FieldOfView] =
      parseThroughNonnegativeInt("FieldOfView")(FieldOfView.apply)

    /** Lift an ordinary integer into field of view wrapper, erroring if invalid.
      */
    def unsafeLift: Int => FieldOfView =
      NonnegativeInt.unsafe `andThen` FieldOfView.apply
  end FieldOfView

  private[gerlib] type PositionNamePunctuation = StrictEqual['.'] | StrictEqual['-'] |
    StrictEqual['_']

  /** A position name character must be alphanumeric, a hyphen, or an underscore.
    */
  private[gerlib] type PositionNameCharacterConstraint = Digit | Letter | PositionNamePunctuation

  /** A position name must be nonempty and contain only certain characters; furthermore, it must not
    * start with a hyphen for potential ambiguity with a negative number. We also exclude a number
    * in scientific notation.
    */
  private type PositionNameConstraint = Not[Empty] & ForAll[PositionNameCharacterConstraint] &
    Not[Match["-?[0-9]+"]] & // Exclude integers.
    Not[Match["-?[0-9]+\\.[0-9]+"]] & // Exclude decimals.
    Not[
      Match["-?[0-9]\\.[0-9]+E-?[0-9]{1,3}"]
    ] & // Exclude decimal scientific notation.
    Not[Match["-?[0-9]E-?[0-9]{1,3}"]] // Exclude integer scientific notation.

  /** The name of a position / field of view is a string whose characters all fulfill the
    * constraint.
    */
  final case class PositionName(
      private[imaging] get: String :| PositionNameConstraint
  ) extends FieldOfViewLike derives Order

  /** Put instances here since the refinement is opaque, so underlying String won't be visible
    * elsewhere.
    */
  object PositionName:
    /** Inline variant when argument is inlineable */
    inline def apply(
        inline get: String :| PositionNameConstraint
    ): PositionName = new PositionName(get)

    /** Refine through [[scala.util.Either]] as the monadic type. */
    def parse: Parser[PositionName] =
      (s: String) =>
        s
          .refineEither[PositionNameConstraint]
          .bimap(
            msg => s"Could not refine string ($s) as position name: $msg",
            PositionName.apply
          )

    /** Refine the string, then wrap it. */
    def unsafe = (s: String) => parse(s).fold(msg => throw IllegalArgumentException(msg), identity)
  end PositionName

end imaging
