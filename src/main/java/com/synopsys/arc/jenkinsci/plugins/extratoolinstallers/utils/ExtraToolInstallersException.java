/*
 * The MIT License
 *
 * Copyright 2013 Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
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
package com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.utils;

import hudson.tools.ToolInstaller;
import java.io.IOException;

/**
 * Just an exception type for issues happening in the plugin.
 * @author Oleg Nenashev
 */
public class ExtraToolInstallersException extends IOException {
    private static final long serialVersionUID = -6131475303804497293L;
    ToolInstaller installer;
    
    public ExtraToolInstallersException(ToolInstaller installer, String message) {
        super(message);
        this.installer = installer;
    }

    public ExtraToolInstallersException(ToolInstaller installer, Throwable cause) {
        super(cause);
        this.installer = installer;
    }

    public ExtraToolInstallersException(ToolInstaller installer, String message, Throwable cause) {
        super(message, cause);
        this.installer = installer;
    }

    public ToolInstaller getInstaller() {
        return installer;
    } 
}
