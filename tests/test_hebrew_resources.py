"""Guard the Hebrew Home layout's required views."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


RES = Path(__file__).resolve().parents[1] / "launcher/app/src/main/res"
ANDROID_ID = "{http://schemas.android.com/apk/res/android}id"


def views(folder):
    return {v.attrib[ANDROID_ID]: v.tag
            for v in ET.parse(RES / folder / "activity_main.xml").iter()
            if ANDROID_ID in v.attrib}


class HebrewResourcesTest(unittest.TestCase):
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
