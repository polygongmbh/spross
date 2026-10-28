package net.spross.kern.design

/** Which of a split window's two panes a section belongs to; each platform stands [Primary] beside its own controls. */
enum class PaneRole { Primary, Secondary }

/**
 * Home's sections in the one column's order, each with the pane it takes where the window splits.
 * The primary pane holds what acts on the day: the day's card, the drills and the conversation;
 * the secondary holds the rest, headed by the greeting.
 * [VoiceUpgrade] is the hint that a better voice is a free download, where the platform has one.
 */
enum class HomeSection(val role: PaneRole) {
    Header(PaneRole.Secondary),
    VoiceUpgrade(PaneRole.Secondary),
    Day(PaneRole.Primary),
    Listening(PaneRole.Secondary),
    Drills(PaneRole.Primary),
    Conversation(PaneRole.Primary),
    Trees(PaneRole.Secondary),
}

/**
 * Home's sections by pane, each in the one column's order.
 * Where the window splits, each pane holds its role's sections;
 * in one column, [primary] holds every section and [secondary] none.
 */
class HomeColumns(split: Boolean) {
    val primary: List<HomeSection> =
        if (split) HomeSection.entries.filter { it.role == PaneRole.Primary } else HomeSection.entries
    val secondary: List<HomeSection> =
        if (split) HomeSection.entries.filter { it.role == PaneRole.Secondary } else emptyList()
}
