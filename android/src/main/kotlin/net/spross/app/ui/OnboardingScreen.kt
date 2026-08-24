package net.spross.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import net.spross.app.AppModel
import net.spross.app.Chrome
import net.spross.kern.catalog.LanguageChoices

/** The three pages of the first run, in the order they are walked. */
private enum class Page { Languages, Why, FirstRound }

/** Long enough to read as a page turn, short enough that the tap still feels answered — iOS's. */
private const val PAGE_FADE_MS = 200

/**
 * First launch, in three pages: the pair, what Spross is for ([PrinciplesPage]),
 * and what a round asks of you ([FirstRoundPage]).
 * Only the last one commits — the two before it merely turn the page —
 * so the box is joined once, behind something worth reading.
 *
 * Chrome speaks the language being picked: the device language greets first
 * (it seeds the source pick), and every later tap re-renders in that pick —
 * English for a source we have no chrome for yet.
 * Coverage-driven: sources are languages with at least one learnable target, and neither
 * side hides the other's pick — choosing it swaps the two selections ([LanguageChoices]).
 *
 * It is reached once, on a device with no profile yet: a learner who wants another pair
 * later changes it in the box's own settings, where the pickers stand beside everything
 * else the box is configured by.
 *
 * One side is open at a time and the other shows its pick as a row that opens it:
 * both lists at once is more of the screen than the two questions are worth, and a
 * pick answers its own question — so choosing a source hands the screen to the target.
 * The known side opens folded, the device language being a good guess already.
 */
@Composable
fun OnboardingScreen(model: AppModel) {
    val catalog = model.catalog ?: return
    val initialSource = model.defaultSource(catalog)
    var page by rememberSaveable { mutableStateOf(Page.Languages) }
    var source by rememberSaveable { mutableStateOf(initialSource) }
    var target by rememberSaveable {
        mutableStateOf(catalog.availableTargets(initialSource).firstOrNull()?.code)
    }
    var pickingSource by rememberSaveable { mutableStateOf(false) }
    // The device's own guess, where it is named after somebody ([DeviceName]) — offered
    // filled in, and worth nothing until the last page commits it.
    var name by rememberSaveable { mutableStateOf(model.suggestedLearnerName().orEmpty()) }
    // Plain remember: a restored `true` would outlive the model's coroutine and leave a
    // spinner nothing ever resolves. Rotation keeps the activity (`configChanges`), so
    // the only way back here is process death, where the join is gone anyway.
    var starting by remember { mutableStateOf(false) }
    val chrome = remember(source) { Chrome.forSource(source) }
    val choices = remember(catalog, source, target) {
        LanguageChoices.targetChoices(catalog, LanguageChoices.Selection(source, target))
    }

    fun apply(sel: LanguageChoices.Selection) {
        source = sel.source
        target = sel.target
    }

    val label: (String) -> String = { LanguageChoices.pickerRow(it, catalog.languages[it]) }

    // why: without a handler, back on a reading page finishes the activity and throws the
    // language pick away. Page one keeps the default — leaving a first-run screen with no
    // profile is what back means there.
    BackHandler(enabled = page != Page.Languages) {
        page = if (page == Page.FirstRound) Page.Why else Page.Languages
    }

    AnimatedContent(
        targetState = page,
        // why: `using null` drops the default size transform — the three pages differ in
        // height, and animating the container drags the button up the screen mid-fade.
        transitionSpec = {
            fadeIn(tween(PAGE_FADE_MS)) togetherWith fadeOut(tween(PAGE_FADE_MS)) using null
        },
        label = "onboarding",
    ) { current ->
        when (current) {
            Page.Languages -> OnboardingStoryPage {
                OnboardingHero("👋", chrome.chooseTitle, chrome.chooseSubtitle)

                PickerSection(
                    heading = chrome.iSpeak,
                    open = pickingSource,
                    options = catalog.coveredSources(),
                    selected = source,
                    chrome = chrome,
                    label = label,
                    onOpen = { pickingSource = true },
                ) { code ->
                    apply(
                        LanguageChoices.pickSource(
                            catalog,
                            LanguageChoices.Selection(source, target),
                            code,
                        ),
                    )
                    pickingSource = false
                }

                PickerSection(
                    heading = chrome.iLearn,
                    open = !pickingSource,
                    options = choices,
                    selected = target,
                    chrome = chrome,
                    label = label,
                    onOpen = { pickingSource = false },
                ) { code ->
                    val picked = LanguageChoices.Selection(source, target)
                    apply(LanguageChoices.pickTarget(picked, code))
                }

                NameSection(chrome, name) { name = it }

                OnboardingPrimary(chrome.next, enabled = target != null) {
                    if (target != null) page = Page.Why
                }
            }

            Page.Why -> PrinciplesPage(
                chrome = chrome,
                onNext = { page = Page.FirstRound },
                onBack = { page = Page.Languages },
            )

            Page.FirstRound -> FirstRoundPage(
                chrome = chrome,
                busy = starting,
                onStart = {
                    // why: the join activates the profile and raises the first round, both
                    // off this screen — the spinner stands in until that screen arrives.
                    target?.let {
                        starting = true
                        // A blank field is no name at all, which is what it looked like.
                        model.renameLearner(name)
                        model.completeOnboarding(source, it, thenPractice = true)
                    }
                },
                onBack = { page = Page.Why },
            )
        }
    }
}

/**
 * The one question the first page does not need answered: what to call the learner.
 *
 * It gates nothing — the way on is the pair alone. The greeting has a
 * wording for a learner it cannot name, so an empty field costs nothing at all.
 */
@Composable
private fun NameSection(chrome: Chrome, name: String, onName: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.xs)) {
        Text(chrome.learnerNameQuestion, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            singleLine = true,
            placeholder = { Text(chrome.learnerNamePlaceholder) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * One side of the pair: its question, and either the languages to choose from or the
 * chosen one as the row that opens them again.
 */
@Composable
private fun PickerSection(
    heading: String,
    open: Boolean,
    options: List<String>,
    selected: String?,
    chrome: Chrome,
    label: (String) -> String,
    onOpen: () -> Unit,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DlSpace.xs)) {
        Text(heading, style = MaterialTheme.typography.titleSmall)
        if (open) {
            options.forEach { code ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = selected == code,
                        role = Role.RadioButton,
                        onClick = { onPick(code) },
                    ),
                ) {
                    RadioButton(selected = selected == code, onClick = null)
                    Text(label(code), style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)
                    .clickable(onClick = onOpen)
                    .semantics { stateDescription = chrome.stateCollapsed },
            ) {
                Text(
                    selected?.let(label).orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    SprossIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
