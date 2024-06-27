package at.ac.oeaw.imba.gerlich.gerlib.imaging

import cats.*
import cats.derived.*
import at.ac.oeaw.imba.gerlich.gerlib.numeric.*

/** Semantic wrapper around value representing 0-based imaging timepoint */
final case class ImagingTimepoint(get: NonnegativeInt) derives Order, Show

/** Helpers for working with imaging timepoints */
object ImagingTimepoint:
    /** Wrap the given value as an imaging timepoint, if it's valid as one. */
    def parse: String => Either[String, ImagingTimepoint] = 
        parseThroughNonnegativeInt("ImagingTimepoint")(ImagingTimepoint.apply)
end ImagingTimepoint