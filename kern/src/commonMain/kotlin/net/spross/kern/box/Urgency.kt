package net.spross.kern.box

import net.spross.kern.model.CardScheduling

/**
 * Which card wants attention first: the weakest memory, stability ascending.
 *
 * The growth stages are stability bands, so this orders them as well —
 * a lapsed word's post-lapse stability already puts it near the front,
 * and a lapse that kept its stability ranks where that stability stands.
 * Ties keep the order they arrive in.
 * A card with no memory has no place here; a caller that ranks those says where.
 */
internal object Urgency {
    val weakestFirst: Comparator<CardScheduling> = compareBy { it.memory?.stability ?: 0.0 }
}
