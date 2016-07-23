/*
 * The MIT License
 *
 * Copyright 2016 Martin Hjelmqvist.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.installers;

import hudson.FilePath;
import java.net.URL;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * Testing the extraction process.
 *
 * @author Martin Hjelmqvist <martin@hjelmqvist.eu>.
 */
public class RarFilePathTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /**
     * Performs an installation/extraction of a sample tool.
     * @throws Exception If the installation fails, an exception is thrown.
     */
    @Test
    public void installIfNecessaryPerformsInstallation() throws Exception {
        final RarFilePath d = new RarFilePath(new FilePath(temp.getRoot()));
        final URL toolHome = getClass().getResource("testArchive.rar");
        assertTrue("No installation performed.", d.installIfNecessaryFrom(toolHome, null, "Unpacking " + toolHome.toString() + " to " + temp.getRoot().toString()));
        temp.delete();
    }
}
