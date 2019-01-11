/*
 * Copyright 2013 Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.utils;

import hudson.EnvVars;
import hudson.model.Node;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.tools.ToolInstaller;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Provides parsing of environment variables in input string.
 * @author Oleg Nenashev
 * @since 0.3
 */
public class EnvStringParseHelper {
    private EnvStringParseHelper() {};

    /**
     * Resolves tools installation directory using global variables.
     * @param environment Collection of environment variables
     * @param macroString Input path with macro calls
     * @return  Raw string
     * @since 0.3
     */
    public static String substituteEnvVars(String macroString, EnvVars environment)  {
        if (macroString == null) return null;
        if (!macroString.contains("${")) {
            return macroString;
        }
        return environment.expand(macroString);
    }

    /**
     * Substitutes string according to all node properties.
     * @param macroString String to be substituted
     * @param node Node whose properties provide available substitution
     * @return Substituted string
     */
    @Nullable
    public static String substituteNodeVariables(@CheckForNull String macroString, @Nonnull Node node) {
        if (macroString == null) return null;
        if (!macroString.contains("${")) {
            return macroString;
        }

        // Check node properties
        String substitutedString = macroString;
        for (NodeProperty<?> entry : node.getNodeProperties()) {
            substitutedString = substituteNodeProperty(substitutedString, entry);
        }

        // Substitute global variables
        final Jenkins jenkinsInstance = Jenkins.getInstance();
        for (NodeProperty<?> entry : jenkinsInstance.getGlobalNodeProperties()) {
            substitutedString = substituteNodeProperty(substitutedString, entry);
        }

        return substitutedString;
    }

    /**
     * Substitutes string according to node property.
     * @param macroString String to be substituted
     * @param property Node property
     * @return Substituted string
     * @since 0.3
     */
    public static String substituteNodeProperty(String macroString, NodeProperty<?> property) {
        // Get environment variables
        if (property != null && property instanceof EnvironmentVariablesNodeProperty ) {
           EnvironmentVariablesNodeProperty prop = (EnvironmentVariablesNodeProperty)property;
           return substituteEnvVars(macroString, prop.getEnvVars());
        }

        //TODO: add support of other configuration entries or propagate environments
        return macroString;
    }

    /**
     * Checks that all variables have been resolved.
     * @param installer The installer that's doing the checking.
     * @param stringName The human-friendly name of the string being checked.
     * @param macroString The string contents that should be checked.
     * @throws ExtraToolInstallersException String validation failed (there are unresolved variables)
     * @since 0.3
     */
    public static void checkStringForMacro(ToolInstaller installer, String stringName, String macroString)
            throws ExtraToolInstallersException {
        // Check consistency and throw errors
        if (macroString.contains("${")) {
           throw new ExtraToolInstallersException(installer, "Can't resolve all variables in "+stringName+" string. Final state: "+macroString);
        }
    }
}
