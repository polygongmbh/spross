package net.spross.kern.box

/**
 * Which group sections ([groups], by [AreaGroupSection.id]) and which areas of the box browser stand open.
 * The learner's own taps fold and unfold freely; kern decides the fold the browser opens on
 * and the one a named area turns it into.
 */
data class BoxFold(val groups: Set<String>, val areas: Set<String>) {

    /**
     * An area named by a search hit or a tree: its group and the area itself open INSTEAD of whatever
     * stood open — the learner said which area they meant, and every other open shelf is only weight
     * to lay out before the browser can bring this one into view.
     * The own words have no shelf to unfold, so naming them leaves the fold as it is.
     */
    fun revealing(area: String, sections: List<AreaGroupSection>): BoxFold {
        if (area == OwnWords.AREA) return this
        val group = sections.firstOrNull { area in it.areas }?.id
        return BoxFold(group?.let { setOf(it) } ?: groups, setOf(area))
    }

    companion object {
        /**
         * The fold the browser opens on, read once — a group that folded itself shut again
         * as the learner works would be worse than one that opened on the wrong shelf.
         * An area named on the way in opens with its group INSTEAD of the default one
         * ([BoxBrowser.defaultExpandedGroupId]).
         */
        fun opening(sections: List<AreaGroupSection>, stats: BoxStatistics, revealArea: String?): BoxFold {
            val group = revealArea?.let { area -> sections.firstOrNull { area in it.areas }?.id }
                ?: BoxBrowser.defaultExpandedGroupId(sections, stats)
            return BoxFold(setOfNotNull(group), setOfNotNull(revealArea))
        }
    }
}
