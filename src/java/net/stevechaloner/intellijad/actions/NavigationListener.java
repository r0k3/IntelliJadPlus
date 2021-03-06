/*
 * Copyright 2007 Steve Chaloner
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package net.stevechaloner.intellijad.actions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.stevechaloner.intellijad.IntelliJadConstants;
import net.stevechaloner.intellijad.IntelliJadResourceBundle;
import net.stevechaloner.intellijad.config.Config;
import net.stevechaloner.intellijad.config.NavigationTriggeredDecompile;
import net.stevechaloner.intellijad.decompilers.DecompilationChoiceListener;
import net.stevechaloner.intellijad.decompilers.DecompilationDescriptor;
import net.stevechaloner.intellijad.decompilers.DecompilationDescriptorFactory;
import net.stevechaloner.intellijad.environment.EnvironmentContext;
import net.stevechaloner.intellijad.util.Exclusion;
import net.stevechaloner.intellijad.util.PluginUtil;

import net.stevechaloner.intellijad.vfs.MemoryVF;
import net.stevechaloner.intellijad.vfs.MemoryVFS;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for navigation events such as files being opened or closed and reacts accordingly based on the individual file.
 *
 * @author Steve Chaloner
 */
public class NavigationListener implements FileEditorManagerListener
{
    /**
     * Handler classes for the result of navigation actions.
     */
    private final Map<NavigationTriggeredDecompile, NavigationOption> navigationOptions = new HashMap<NavigationTriggeredDecompile, NavigationOption>()
    {
        {
            put(NavigationTriggeredDecompile.ALWAYS,
                new NavigationOption()
                {
                    public void execute(@NotNull Config config,
                                        @NotNull DecompilationDescriptor descriptor)
                    {
                        boolean excluded = new Exclusion(config).isExcluded(descriptor);
                        if (!excluded)
                        {
                            decompilationListener.decompile(new EnvironmentContext(project),
                                                            descriptor);
                        }
                    }
                });
            put(NavigationTriggeredDecompile.ON_DEMAND,
                new NavigationOption()
                {
                    public void execute(@NotNull Config config,
                                        @NotNull DecompilationDescriptor descriptor)
                    {
                        // no-op
                    }
                });
            put(NavigationTriggeredDecompile.NEVER,
                new NavigationOption()
                {
                    public void execute(@NotNull Config config,
                                        @NotNull DecompilationDescriptor descriptor)
                    {
                        // no-op
                    }
                });
        }
    };

    /**
     * The decompilation listener.
     */
    @NotNull
    private final DecompilationChoiceListener decompilationListener;

    /**
     * The project this listener is interested in.
     */
    @NotNull
    private final Project project;

    /**
     * Initialises a new instance of this class.
     *
     * @param project               the project this object is listening for
     * @param decompilationListener the decompilation listener
     */
    public NavigationListener(@NotNull Project project,
                              @NotNull DecompilationChoiceListener decompilationListener)
    {
        this.project = project;
        this.decompilationListener = decompilationListener;
    }

    private NavigationTriggeredDecompile getDecompileMode() {
        Config config = PluginUtil.getConfig(project);
        return NavigationTriggeredDecompile.getByName(config.getDecompileOnNavigation());
    }

    /** {@inheritDoc} */
    public void fileOpened(FileEditorManager fileEditorManager,
                           VirtualFile file)
    {
        if (file != null && "class".equals(file.getExtension()))
        {
            Config config = PluginUtil.getConfig(project);
            DecompilationDescriptor dd = DecompilationDescriptorFactory.getFactoryForFile(file).create(file);
            NavigationOption navigationOption = navigationOptions.get(getDecompileMode());
            navigationOption.execute(config, dd);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void fileClosed(FileEditorManager fileEditorManager,
                           VirtualFile virtualFile)
    {
        if (virtualFile instanceof MemoryVF) {
            Config config = PluginUtil.getConfig(project);
            if (!config.isKeepDecompiledToMemory()) {
                MemoryVFS vfs = (MemoryVFS) VirtualFileManager.getInstance().getFileSystem(IntelliJadConstants.INTELLIJAD_PROTOCOL);
                try {
                    vfs.deleteFile(this, virtualFile);
                }
                catch (IOException e) {
                    Logger.getInstance(getClass().getName()).error(e);
                }
            }
        }
    }

    /** {@inheritDoc} */
    public void selectionChanged(FileEditorManagerEvent fileEditorManagerEvent)
    {
        // no-op
    }

    /**
     * Handles the result of a navigation-based action decision.
     */
    private interface NavigationOption
    {
        /**
         * Handle the choice.
         *
         * @param config     the configutation
         * @param descriptor the descriptor of the class to decompile
         */
        void execute(@NotNull Config config,
                     @NotNull DecompilationDescriptor descriptor);
    }
}