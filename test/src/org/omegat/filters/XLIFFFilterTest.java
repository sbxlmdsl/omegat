/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2008-2013 Alex Buloichik
               2015 Aaron Madlon-Kay
               Home page: http://www.omegat.org/
               Support center: http://groups.yahoo.com/group/OmegaT/

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.filters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.omegat.core.Core;
import org.omegat.core.data.IProject;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.core.statistics.StatCount;
import org.omegat.core.statistics.StatisticsSettings;
import org.omegat.filters2.ITranslateCallback;
import org.omegat.filters2.TranslationException;
import org.omegat.filters3.xml.xliff.XLIFFDialect;
import org.omegat.filters3.xml.xliff.XLIFFFilter;
import org.omegat.filters3.xml.xliff.XLIFFOptions;
import org.omegat.util.PatternConsts;
import org.omegat.util.Preferences;
import org.omegat.util.StaticUtils;
import org.xml.sax.SAXException;

public class XLIFFFilterTest extends TestFilterBase {
    XLIFFFilter filter;

    @Before
    public final void setUp() {
        filter = new XLIFFFilter();
        XLIFFDialect dialect = (XLIFFDialect) filter.getDialect();
        dialect.defineDialect(new XLIFFOptions(new TreeMap<String, String>()));
    }

    @Test
    public void testParse() throws Exception {
        parse(filter, "test/data/filters/xliff/file-XLIFFFilter.xlf");
    }

    @Test
    public void testTranslate() throws Exception {
        translateXML(filter, "test/data/filters/xliff/file-XLIFFFilter.xlf");
        translateXML(filter, "test/data/filters/xliff/file-XLIFFFilter-SMP.xlf");
    }

    @Test
    public void testLoad() throws Exception {
        String f = "test/data/filters/xliff/file-XLIFFFilter.xlf";
        IProject.FileInfo fi = loadSourceFiles(filter, f);

        checkMultiStart(fi, f);
        checkMulti("tr1=This is test", null, null, "", "tr2=test2", null);
        checkMulti("tr2=test2", null, null, "tr1=This is test", "", null);
        checkMultiEnd();
    }

    @Test
    public void testTags() throws Exception {
        String f = "test/data/filters/xliff/file-XLIFFFilter-tags.xlf";
        IProject.FileInfo fi = loadSourceFiles(filter, f);

        SourceTextEntry ste;
        checkMultiStart(fi, f);
        checkMultiNoPrevNext("Link to <m0>http://localhost</m0>.", null, null, null); // #1988732
        checkMultiNoPrevNext("About <b0>Gandalf</b0>", null, null, "7"); // #1988732
        checkMultiNoPrevNext("<i0>Tags</i0> translation zz<i1>2</i1>z <b2>-NONTRANSLATED", null, null, null);
        checkMultiNoPrevNext("one <a0> two </b1> three <c2> four </d3> five", null, null, null);
        ste = checkMultiNoPrevNext("About <m0>Gandalf</m0> and <m1>other</m1>.", null, null, null);
        assertEquals(3, ste.getProtectedParts().length);
        assertEquals("<m0>Gandalf</m0>", ste.getProtectedParts()[0].getTextInSourceSegment());
        assertEquals("<mrk mtype=\"protected\">Gandalf</mrk>",
                ste.getProtectedParts()[0].getDetailsFromSourceFile());
        assertEquals("Gandalf", ste.getProtectedParts()[0].getReplacementMatchCalculation());
        assertEquals("<m1>", ste.getProtectedParts()[1].getTextInSourceSegment());
        assertEquals("<mrk mtype=\"other\">", ste.getProtectedParts()[1].getDetailsFromSourceFile());
        assertEquals(StaticUtils.TAG_REPLACEMENT, ste.getProtectedParts()[1].getReplacementMatchCalculation());
        assertEquals("</m1>", ste.getProtectedParts()[2].getTextInSourceSegment());
        assertEquals("</mrk>", ste.getProtectedParts()[2].getDetailsFromSourceFile());
        assertEquals(StaticUtils.TAG_REPLACEMENT, ste.getProtectedParts()[2].getReplacementMatchCalculation());
        checkMultiNoPrevNext("one <o0>two</o0> three", null, null, null);
        checkMultiNoPrevNext("one <t0/> three", null, null, null);
        checkMultiNoPrevNext("one <w0/> three", null, null, null);
        checkMultiNoPrevNext("Nested tags: before <g0><g1><x2/></g1></g0> after", null, null, null);
        checkMultiNoPrevNext("<m0>Check protected-only tag reading</m0>", null, null, null);
        checkMultiEnd();

        File inFile = new File("test/data/filters/xliff/file-XLIFFFilter-tags.xlf");
        filter.translateFile(inFile, outFile, new TreeMap<String, String>(), context,
                new ITranslateCallback() {
                    public String getTranslation(String id, String source, String path) {
                        return source.replace("NONTRANSLATED", "TRANSLATED");
                    }

                    public String getTranslation(String id, String source) {
                        return source.replace("NONTRANSLATED", "TRANSLATED");
                    }

                    public void linkPrevNextSegments() {
                    }

                    public void setPass(int pass) {
                    }
                });
        File trFile = new File(outFile.getPath() + "-translated");
        List<String> lines = Files.lines(inFile.toPath()).map(line -> line.replace("NONTRANSLATED", "TRANSLATED"))
                .collect(Collectors.toList());
        Files.write(trFile.toPath(), lines);
        compareXML(trFile, outFile);
    }

    @Test
    public void testTagOptimization() throws Exception {
        String f = "test/data/filters/xliff/file-XLIFFFilter-tags-optimization.xlf";

        Core.getFilterMaster().getConfig().setRemoveTags(false);
        IProject.FileInfo fi = loadSourceFiles(filter, f);

        checkMultiStart(fi, f);
        checkMultiNoPrevNext("<b0>The text of a segment<b1>.<b2>", null, null, null);
        checkMultiNoPrevNext("<b0>The text of a segment<b1>.<b2><b3><b4>", null, null, null);
        checkMultiNoPrevNext("<b0>Link to a <a1>reference</a1></b0>", null, null, null);
        checkMultiEnd();
        translateXML(filter, f);

        Core.getFilterMaster().getConfig().setRemoveTags(true);
        fi = loadSourceFiles(filter, f);

        checkMultiStart(fi, f);
        checkMultiNoPrevNext("The text of a segment<b0>.", null, null, null);
        checkMultiNoPrevNext("The text of a segment<b0>.", null, null, null);
        checkMultiNoPrevNext("Link to a <a0>reference</a0>", null, null, null);
        checkMultiEnd();
        translateXML(filter, f);
    }

    @Test
    public void testStatCounting() throws Exception {
        String f = "test/data/filters/xliff/file-XLIFFFilter-statcount.xlf";

        StatisticsSettings.setCountingProtectedText(true);
        StatisticsSettings.setCountingCustomTags(true);
        IProject.FileInfo fi = loadSourceFiles(filter, f);
        StatCount counts = new StatCount(fi.entries.get(0));
        assertEquals(4, counts.words);
    }

    @Test
    public void testStatCountingNoProtectedText() throws Exception {
        String f = "test/data/filters/xliff/file-XLIFFFilter-statcount.xlf";

        StatisticsSettings.setCountingProtectedText(false);
        StatisticsSettings.setCountingCustomTags(true);
        IProject.FileInfo fi = loadSourceFiles(filter, f);
        StatCount counts = new StatCount(fi.entries.get(0));
        assertEquals(2, counts.words);
    }

    @Test
    public void testStatCountingNoCustomTags() throws Exception {
        String f = "test/data/filters/xliff/file-XLIFFFilter-statcount.xlf";

        StatisticsSettings.setCountingProtectedText(true);
        StatisticsSettings.setCountingCustomTags(false);
        Preferences.setPreference(Preferences.CHECK_CUSTOM_PATTERN, "CUSTOM");
        PatternConsts.updatePlaceholderPattern();
        IProject.FileInfo fi = loadSourceFiles(filter, f);
        StatCount counts = new StatCount(fi.entries.get(0));
        assertEquals(3, counts.words);
    }

    /*
     * Test that an XLIFF file containing an invalid character (in this case
     * U+0008) will cause the parser to die with a SAXParseException. This isn't
     * actually important in and of itself; we wouldn't mind if the parser was
     * lenient because we filter bad XML characters out on our own later. This
     * is just necessary to set a baseline for testInvalidXMLOnWeirdPath().
     */
    @Test
    public void testInvalidXML() throws Exception {
        String f = "test/data/filters/xliff/file-XLIFFFilter-invalid-content.xlf";

        try {
            loadSourceFiles(filter, f);
            fail("Should have died due to invalid XML character");
        } catch (TranslationException ex) {
            assertTrue(wasCausedBy(ex, SAXException.class));
        }
    }

    /*
     * Issue reported by Jean-Christophe Helary: When a file with invalid
     * content is on a path that contains both spaces and "non-path" characters,
     * a URISyntaxException was reported about the path instead of the
     * SAXParseException about the file content.
     *
     * This may only fail with a particular underlying parser implementation, as
     * it depends on a particular codepath in
     * com.sun.org.apache.xerces.internal.impl.XMLEntityManager and
     * com.sun.org.apache.xerces.internal.util.URI where it tries to be lenient
     * in its acceptance of not-quite-valid URIs as system IDs.
     */
    @Test
    public void testInvalidXMLOnWeirdPath() throws Exception {
        String f = "test/data/filters/xliff/file-XLIFFFilter-invalid-content.xlf";

        File tmpDir = Files.createTempDirectory("omegat").toFile();
        assertTrue(tmpDir.isDirectory());
        File weirdDir = new File(tmpDir, "a b\u2603"); // U+2603 SNOWMAN
        File testFile = new File(weirdDir, "file-XLIFFFilter-invalid-content.xlf");
        FileUtils.copyFile(new File(f), testFile);
        assertTrue(testFile.isFile());

        try {
            loadSourceFiles(filter, testFile.getAbsolutePath());
            fail("Should have died due to invalid XML character");
        } catch (TranslationException ex) {
            assertTrue(wasCausedBy(ex, SAXException.class));
            assertFalse(wasCausedBy(ex, URISyntaxException.class));
        }

        FileUtils.deleteDirectory(tmpDir);
    }

    private static boolean wasCausedBy(Throwable ex, Class<?> cls) {
        Throwable cause = ex.getCause();
        if (cause == null) {
            return false;
        } else if (cause.getClass().equals(cls)) {
            return true;
        } else {
            return wasCausedBy(cause, cls);
        }
    }

    @Test
    public void testProperties() throws Exception {
        String f = "test/data/filters/xliff/file-XLIFFFilter-properties.xlf";
        IProject.FileInfo fi = loadSourceFiles(filter, f);

        // Check reading as properties. We don't really care about the order of the content in the parsed
        // properties array (as long as the key=value pairs are consistent), so we do lose checking.
        checkMultiStart(fi, f);
        checkMultiProps("tr1=This is test", null, null, "", "tr2=test2", "note", "foo", "group", "bazinga");
        checkMultiProps("tr2=test2", null, null, "tr1=This is test", "", "note", "bar", "resname", "baz",
                "group", "bazinga");
        checkMultiEnd();

        // Check reading as old comment string blobs. We don't really care about the order of the content in
        // the parsed properties array, but the way the test currently works, it will break if the order
        // changes.
        checkMultiStart(fi, f);
        checkMulti("tr1=This is test", null, null, "", "tr2=test2", "foo\nbazinga");
        checkMulti("tr2=test2", null, null, "tr1=This is test", "", "bar\nbazinga\nbaz");
        checkMultiEnd();
    }
}
