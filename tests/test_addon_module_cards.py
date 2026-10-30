"""Every module card the add-ons ship is one the launcher will accept.

`docs/specs/card.md` is the contract an app declares its card against, and `Cards.java` refuses a
card whole: one character outside the set a field allows and `parse()` throws, `read()` catches it per
card and logs it under `TvHomeCards`, and the app falls back to its ordinary tile. Nothing in the
repository read the cards the tree itself ships - `grep -rn module_card tests/` found the string in
two files and neither opens one - so addons/bluetooth-connect's caption, "Connect / disconnect",
carried a slash, which no caption may use:

    com.<pkg>: caption has characters outside A-Z a-z 0-9 space . & + ' -

and the card its README promises ("its tile reads \\"Bluetooth\\" with a teal glow") never appeared on
a TV, with nothing anywhere to notice. `docs/modules.md` - the how-to a card author follows - gave
the label's characters and said only "up to 32 characters" for the caption, so the how-to produced
that caption.

The limits and the alphabet are read out of `Cards.java` instead of being copied in, and the two
lengths are read out of the spec's own table, so a card this test passes is one the launcher takes,
and the how-to and the contract are held to the same numbers.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ADDONS = ROOT / "addons"
CARDS_JAVA = ROOT / "launcher/app/src/main/java/com/gathin/tvhome/Cards.java"
SPEC = ROOT / "docs" / "specs" / "card.md"

FIELDS = ("spec", "label", "caption", "accent")


def card_files(root=ADDONS):
    """Every module card the add-ons in `root` ship, newest path order."""
    return sorted(Path(root).glob("*/app/src/main/res/xml/module_card.xml"))


def java_constants(path=CARDS_JAVA):
    """The limits, the alphabet and the reserved names, read out of Cards.java.

    A copy of these numbers here would be a second place to keep in step; the launcher's own
    constants are what a card is judged by.
    """
    text = Path(path).read_text(encoding="utf-8")

    def number(name):
        m = re.search(r"\b%s\s*=\s*(\d+)\s*;" % name, text)
        assert m, "%s is not a number in Cards.java" % name
        return int(m.group(1))

    def string(name):
        m = re.search(r'\b%s\s*=\s*"([^"]*)"\s*;' % name, text)
        assert m, "%s is not a string in Cards.java" % name
        return m.group(1)

    m = re.search(r"RESERVED_ANYWHERE\s*=\s*\{([^}]*)\}", text)
    assert m, "RESERVED_ANYWHERE is not a literal array in Cards.java"
    anywhere = tuple(re.findall(r'"([^"]+)"', m.group(1)))

    m = re.search(r"RESERVED_EXACT\s*=\s*new HashSet<>\(Arrays\.asList\(([^)]*)\)", text)
    assert m, "RESERVED_EXACT is not an inline list in Cards.java"
    exact = set(re.findall(r'"([^"]+)"', m.group(1)))

    return {
        "spec": number("SPEC_SUPPORTED"),
        "label_max": number("LABEL_MAX"),
        "caption_max": number("CAPTION_MAX"),
        "punctuation": string("TEXT_PUNCTUATION"),
        "anywhere": anywhere,
        "exact": exact,
        "prefix": string("RESERVED_PREFIX"),
    }


def spec_lengths(path=SPEC):
    """(label, caption) maxima as the contract's Fields table states them."""
    text = Path(path).read_text(encoding="utf-8")
    label = re.search(r"^\|\s*`label`\s*\|[^|]*\|\s*1 to (\d+) characters", text, re.M)
    caption = re.search(r"^\|\s*`caption`\s*\|[^|]*\|[^|]*?Up to (\d+) characters", text, re.M)
    assert label, "the spec's Fields table states no label length"
    assert caption, "the spec's Fields table states no caption length"
    return int(label.group(1)), int(caption.group(1))


def fold(value):
    """The folded form Cards.fold() compares: lower case, letters and digits only."""
    return re.sub(r"[^a-z0-9]", "", value.lower())


def is_text(value, punctuation):
    """Whether every character is one a label or caption may hold: ASCII letter, digit, space, punctuation."""
    return all(c.isascii() and (c.isalnum() or c == " " or c in punctuation) for c in value)


def read(path):
    """The root element of the card at `path`, or (None, [the reason XML refused it])."""
    try:
        return ET.parse(str(path)).getroot(), []
    except ET.ParseError as e:
        return None, ["it is not XML the resource compiler would take: %s" % e]


def problems(path, limits=None, lengths=None, labels=None):
    """Why the launcher would drop the card at `path`; [] when it takes it.

    `labels` maps a folded label to the (add-on, label) already using it, which is how a second card
    ends up dropped on the TV: two cards share a folded name and neither has an owner decision.
    """
    limits = limits or java_constants()
    lengths = lengths or spec_lengths()
    root, reasons = read(path)
    if root is None:
        return reasons

    addon = Path(path).parents[5].name   # addons/<name>/app/src/main/res/xml/module_card.xml
    if root.tag != "card":
        reasons.append("the root element is <%s>, and the launcher reads <card>" % root.tag)

    for name in root.attrib:
        if "}" in name:
            reasons.append("the attribute %s is in an XML namespace, and the launcher reads none" % name)
        elif name not in FIELDS:
            reasons.append("the attribute %r is not one the launcher reads (%s), so it is ignored "
                           "and the card shows without it" % (name, ", ".join(FIELDS)))

    attrs = {k: v for k, v in root.attrib.items() if "}" not in k}
    spec = attrs.get("spec")
    if spec != str(limits["spec"]):
        reasons.append("spec is %r, and this launcher reads spec %d"
                       % (spec, limits["spec"]))

    label = attrs.get("label")
    if not label:
        reasons.append("there is no label, and a card without one is dropped")
    else:
        folded = fold(label)
        if len(label) > lengths[0]:
            reasons.append("the label %r is longer than %d characters" % (label, lengths[0]))
        if not is_text(label, limits["punctuation"]):
            reasons.append("the label %r has characters outside A-Z a-z 0-9 space %s"
                           % (label, limits["punctuation"]))
        if label != label.strip():
            reasons.append("the label %r starts or ends with a space" % label)
        if not folded:
            reasons.append("the label %r has no letter or digit" % label)
        else:
            for word in limits["anywhere"]:
                if word in folded:
                    reasons.append("the label %r claims endorsement (%r)" % (label, word))
            if folded.startswith(limits["prefix"]) or folded in limits["exact"]:
                reasons.append("the label %r is a name the launcher's own UI uses" % label)
            if labels is not None:
                if folded in labels and labels[folded][0] != addon:
                    reasons.append("the label %r is also %s's, and two cards that share a folded "
                                   "name are dropped until an owner has decided one"
                                   % (label, labels[folded][0]))
                labels.setdefault(folded, (addon, label))

    caption = attrs.get("caption")
    if caption:
        if len(caption) > lengths[1]:
            reasons.append("the caption %r is longer than %d characters" % (caption, lengths[1]))
        if not is_text(caption, limits["punctuation"]):
            reasons.append("the caption %r has characters outside A-Z a-z 0-9 space %s"
                           % (caption, limits["punctuation"]))
        if caption != caption.strip():
            reasons.append("the caption %r starts or ends with a space" % caption)
        for word in limits["anywhere"]:
            if word in fold(caption):
                reasons.append("the caption %r claims endorsement (%r)" % (caption, word))

    accent = attrs.get("accent")
    if accent and not re.fullmatch(r"#[0-9A-Fa-f]{6}", accent):
        reasons.append("the accent %r is not #RRGGBB, so the card is dropped" % accent)
    return reasons


class ShippedCardsTest(unittest.TestCase):
    def test_the_walk_reads_the_cards_this_file_was_written_for(self):
        found = card_files()
        self.assertGreaterEqual(len(found), 25, "the add-ons ship fewer cards than this file knows about")
        names = {p.parents[5].name for p in found}
        self.assertIn("bluetooth-connect", names, "the walk no longer reaches the add-ons")
        self.assertIn("quiet-hours", names)

    def test_the_how_to_and_the_contract_state_the_launcher_s_limits(self):
        limits = java_constants()
        label, caption = spec_lengths()
        self.assertEqual(label, limits["label_max"],
                         "docs/specs/card.md lets a label be %d characters and Cards.java takes %d"
                         % (label, limits["label_max"]))
        self.assertEqual(caption, limits["caption_max"],
                         "docs/specs/card.md lets a caption be %d characters and Cards.java takes %d"
                         % (caption, limits["caption_max"]))

    def test_every_shipped_card_is_one_the_launcher_accepts(self):
        limits = java_constants()
        lengths = spec_lengths()
        labels = {}
        for path in card_files():
            with self.subTest(card=str(path.relative_to(ROOT))):
                self.assertEqual(problems(path, limits, lengths, labels), [])

    def test_every_shipped_card_has_a_label(self):
        for path in card_files():
            with self.subTest(card=str(path.relative_to(ROOT))):
                root, reasons = read(path)
                self.assertEqual(reasons, [])
                self.assertIsNotNone(root)
                self.assertTrue((root.attrib.get("label") or "").strip())


class CardReaderTest(unittest.TestCase):
    """The same readers over cards written here, one shape per test."""

    def check(self, card_xml, addon="demo", limits=None, lengths=None, labels=None):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / addon / "app/src/main/res/xml" / "module_card.xml"
            path.parent.mkdir(parents=True)
            path.write_text(card_xml, encoding="utf-8")
            return problems(path, limits, lengths, labels)

    def test_the_caption_the_tree_shipped_is_named(self):
        reasons = self.check('<card spec="1" label="Bluetooth" caption="Connect / disconnect" />',
                             addon="bluetooth-connect")
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("caption", reasons[0])
        self.assertIn("outside A-Z a-z 0-9 space", reasons[0])

    def test_a_caption_of_32_characters_is_taken_and_33_is_not(self):
        wait = "abcdefghij" * 3 + "ab"
        self.assertEqual(len(wait), 32)
        self.assertEqual(self.check('<card spec="1" label="Demo" caption="%s" />' % wait), [])
        self.assertEqual(len(self.check('<card spec="1" label="Demo" caption="%sx" />' % wait)), 1)

    def test_a_label_of_24_characters_is_taken_and_25_is_not(self):
        label = "abcdefghij" * 2 + "abcd"
        self.assertEqual(len(label), 24)
        self.assertEqual(self.check('<card spec="1" label="%s" />' % label), [])
        reasons = self.check('<card spec="1" label="%sx" />' % label)
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("longer than 24 characters", reasons[0])

    def test_a_spec_this_launcher_does_not_read_is_named(self):
        reasons = self.check('<card spec="2" label="Demo" />')
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("spec is '2'", reasons[0])

    def test_an_attribute_the_launcher_ignores_is_named(self):
        reasons = self.check('<card spec="1" label="Demo" capiton="Watch" />')
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("'capiton'", reasons[0])
        self.assertIn("ignored", reasons[0])

    def test_an_accent_that_is_not_rrggbb_is_named(self):
        self.assertEqual(self.check('<card spec="1" label="Demo" accent="#E8A33D" />'), [])
        reasons = self.check('<card spec="1" label="Demo" accent="E8A33D" />')
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("not #RRGGBB", reasons[0])

    def test_a_reserved_label_is_named(self):
        for label in ("Home", "HDMI 2", "Live TV", "cleanyourtv"):
            with self.subTest(label=label):
                reasons = self.check('<card spec="1" label="%s" />' % label)
                self.assertEqual(len(reasons), 1, reasons)
                self.assertIn("the launcher's own UI uses", reasons[0])

    def test_an_endorsement_word_is_named_in_the_label_and_the_caption(self):
        reasons = self.check('<card spec="1" label="Official Retro" />')
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("endorsement", reasons[0])
        reasons = self.check('<card spec="1" label="Retro" caption="Verified list" />')
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("endorsement", reasons[0])

    def test_two_add_ons_sharing_a_label_are_named(self):
        labels = {}
        self.assertEqual(self.check('<card spec="1" label="Retro" />', addon="one", labels=labels), [])
        reasons = self.check('<card spec="1" label="RETRO" />', addon="two", labels=labels)
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("also one's", reasons[0])

    def test_a_root_that_is_not_card_is_named(self):
        reasons = self.check('<module spec="1" label="Demo" />')
        self.assertIn("the root element is <module>", reasons[0])

    def test_a_card_that_is_not_xml_is_named(self):
        reasons = self.check('<card spec="1" label="Demo"')
        self.assertEqual(len(reasons), 1, reasons)
        self.assertIn("not XML", reasons[0])

    def test_a_card_in_step_is_quiet(self):
        self.assertEqual(self.check('<card spec="1" label="Retro" caption="NES games" accent="#E8A33D" />'), [])


if __name__ == "__main__":
    unittest.main()
