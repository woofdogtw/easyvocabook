## MODIFIED Requirements

### Requirement: word_forms suggestions by language and POS
When the user selects a language + part_of_speech combination, the system SHALL automatically
populate the word_forms section with suggested (empty) label rows based on the canonical label
vocabulary. Both platforms SHALL implement exactly this table, and a combination not listed
SHALL produce no suggestions:

| Language | POS | Suggested labels |
|----------|-----|-----------------|
| en | verb | base_form, past_tense, past_participle, gerund |
| en | noun | singular, plural |
| en | adjective | comparative, superlative |
| ja | verb | dictionary_form, masu_form, ta_form, te_form, nai_form, transitive_pair |
| ja | i-adj | te_form, negative, past |
| ja | na-adj | te_form, negative |

Japanese nouns suggest nothing. They have no plural, so `singular`/`plural` do not apply; a
counter is a property of how a noun is counted rather than a form of the noun, and is not unique
for most nouns; and a particle is determined by a noun's role in a sentence, not by the noun
itself. Such a word is quizzed on its word and reading alone.

Suggestions appear as pre-labelled empty rows. The user may fill, remove, or add custom rows.

#### Scenario: Suggestions appear on POS change
- **WHEN** the user selects language=en, part_of_speech=verb
- **THEN** four word_forms rows appear: base_form, past_tense, past_participle, gerund (all empty)

#### Scenario: Changing POS replaces suggestions
- **WHEN** the user changes part_of_speech from verb to noun (with no values filled in yet)
- **THEN** the verb suggestion rows are replaced with singular/plural rows

#### Scenario: Japanese verb suggests the paired verb
- **WHEN** the user selects language=ja, part_of_speech=verb
- **THEN** a `transitive_pair` row appears among the suggestions, on both platforms

#### Scenario: Japanese noun suggests nothing
- **WHEN** the user selects language=ja, part_of_speech=noun
- **THEN** no suggestion rows are added, on both platforms

## ADDED Requirements

### Requirement: Japanese verb attributes in the edit dialog
When the selected language is Japanese and the part of speech is verb, the add/edit dialog SHALL
offer two additional controls, placed with the other fixed fields rather than in the word_forms
section, because they describe the verb itself:

- **Transitivity type**: a three-way single choice — 自動詞 / 他動詞 / 自他両用 — rendered as
  radio buttons on both platforms. Exactly one option is selected at a time; a two-control
  arrangement is not used because it admits a meaningless "neither selected" state.
- **Verb group**: 五段 (I 類) / 一段 (II 類) / 不規則, shown for reference. It is recorded but
  never quizzed.

Both controls SHALL be hidden for any other language or part of speech, and their values SHALL
be cleared when the word is changed to one.

The paired verb is entered in the word_forms section under the `transitive_pair` label, which is
suggested automatically for Japanese verbs. Leaving it blank records that the verb has no
partner.

#### Scenario: Controls appear for a Japanese verb
- **WHEN** the user selects language=ja and part_of_speech=verb
- **THEN** the transitivity choice and the verb group choice are shown

#### Scenario: Controls hidden for other words
- **WHEN** the user selects language=en, or language=ja with part_of_speech=noun
- **THEN** neither control is shown

#### Scenario: Changing away from a Japanese verb clears the attributes
- **WHEN** a word saved as a Japanese verb is changed to a noun and saved
- **THEN** its stored transitivity and verb group are cleared

#### Scenario: Paired verb is suggested for Japanese verbs
- **WHEN** the user selects language=ja and part_of_speech=verb
- **THEN** a `transitive_pair` row appears among the suggested word_forms rows

#### Scenario: Blank paired verb records "no partner"
- **WHEN** the user leaves the suggested `transitive_pair` row empty and saves
- **THEN** the word is saved with no `transitive_pair` row and no error
