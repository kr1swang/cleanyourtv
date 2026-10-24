"""Guard the Hebrew Home layout's required views."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


RES = Path(__file__).resolve().parents[1] / "launcher/app/src/main/res"
ANDROID_ID = "{http://schemas.android.com/apk/res/android}id"


def views(folder):
    return {v.attrib[ANDROID_ID]: v.tag
            for v in ET.parse(RES / folder / "activity_main.xml").iter()
            if ANDROID_ID in v.attrib}


class HebrewResourcesTest(unittest.TestCase):
    def test_hebrew_strings_cover_default_and_keep_format_arguments(self):
        default = ET.parse(RES / "values/strings.xml").getroot()
        hebrew = {v.attrib["name"]: v for v in ET.parse(RES / "values-iw/strings.xml").getroot()}
        placeholders = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")
        for value in default:
            if value.tag != "string" or value.get("translatable") == "false":
                continue
            name = value.attrib["name"]
            with self.subTest(string=name):
                self.assertIn(name, hebrew)
                if name in hebrew:
                    self.assertEqual(sorted(placeholders.findall(value.text or "")),
                                     sorted(placeholders.findall(hebrew[name].text or "")))

    def test_hebrew_home_keeps_all_default_view_ids_and_types(self):
        default, hebrew = views("layout"), views("layout-iw")
        for view_id, tag in default.items():
            with self.subTest(view=view_id):
                # TightTextClock subclasses TextClock; the Hebrew header keeps the stock widget.
                if tag == "com.gathin.tvhome.TightTextClock":
                    tag = "TextClock"
                self.assertEqual(tag, hebrew.get(view_id))

    def test_home_layouts_have_unique_view_ids(self):
        for folder in ("layout", "layout-iw"):
            with self.subTest(layout=folder):
                ids = [v.attrib[ANDROID_ID] for v in ET.parse(RES / folder / "activity_main.xml").iter()
                       if ANDROID_ID in v.attrib]
                self.assertEqual(len(ids), len(set(ids)))


if __name__ == "__main__":
    unittest.main()
