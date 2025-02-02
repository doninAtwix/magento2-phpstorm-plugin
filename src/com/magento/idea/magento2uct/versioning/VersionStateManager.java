/*
 * Copyright © Magento, Inc. All rights reserved.
 * See COPYING.txt for license details.
 */

package com.magento.idea.magento2uct.versioning;

import com.intellij.openapi.project.Project;
import com.magento.idea.magento2uct.packages.SupportedVersion;
import com.magento.idea.magento2uct.settings.UctSettingsService;
import com.magento.idea.magento2uct.util.php.MagentoTypeEscapeUtil;
import com.magento.idea.magento2uct.versioning.indexes.data.ApiCoverageStateIndex;
import com.magento.idea.magento2uct.versioning.indexes.data.DeprecationStateIndex;
import com.magento.idea.magento2uct.versioning.indexes.data.ExistenceStateIndex;
import com.magento.idea.magento2uct.versioning.indexes.data.VersionStateIndex;
import java.util.LinkedList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class VersionStateManager {

    private static VersionStateManager instance;
    private final DeprecationStateIndex deprecationStateIndex;
    private final ExistenceStateIndex existenceStateIndex;
    private final ApiCoverageStateIndex apiCoverageStateIndex;
    private final Boolean isSetIgnoreFlag;
    private final SupportedVersion currentVersion;
    private final SupportedVersion targetVersion;
    private final List<SupportedVersion> versionsToLoad;

    /**
     * Get instance of the version state manager.
     *
     * @param project Project
     *
     * @return VersionStateManager
     */
    @SuppressWarnings("PMD.AvoidSynchronizedAtMethodLevel")
    public static synchronized VersionStateManager getInstance(
            final @NotNull Project project
    ) { //NOPMD
        final UctSettingsService settingsService = UctSettingsService.getInstance(project);

        if (instance == null
                || !instance.isValidFor(settingsService.shouldIgnoreCurrentVersion(),
                settingsService.getCurrentVersionOrDefault(),
                settingsService.getTargetVersion()
        )) {
            instance = new VersionStateManager(project);
        }
        return instance;
    }

    /**
     * Check if specified FQN exists in the deprecation index.
     *
     * @param fqn String
     *
     * @return boolean
     */
    public boolean isDeprecated(final @NotNull String fqn) {
        return deprecationStateIndex.has(escapeFqn(fqn));
    }

    /**
     * Get deprecated in version for the specified FQN.
     *
     * @param fqn String
     *
     * @return String
     */
    public String getDeprecatedInVersion(final @NotNull String fqn) {
        return deprecationStateIndex.getVersion(escapeFqn(fqn));
    }

    /**
     * Check if specified FQN is exists in the existence index.
     *
     * @param fqn String
     *
     * @return boolean
     */
    public boolean isExists(final @NotNull String fqn) {
        return existenceStateIndex.has(escapeFqn(fqn));
    }

    /**
     * Get removed in version for the specified FQN.
     *
     * @param fqn String
     *
     * @return String
     */
    public String getRemovedInVersion(final @NotNull String fqn) {
        return existenceStateIndex.getVersion(escapeFqn(fqn));
    }

    /**
     * Check if specified FQN is marked as API.
     *
     * @param fqn String
     *
     * @return boolean
     */
    public boolean isApi(final @NotNull String fqn) {
        return apiCoverageStateIndex.has(escapeFqn(fqn));
    }

    /**
     * Version state manager constructor.
     */
    private VersionStateManager(final @NotNull Project project) {
        final UctSettingsService settingsService = UctSettingsService.getInstance(project);
        isSetIgnoreFlag = settingsService.shouldIgnoreCurrentVersion();
        currentVersion = settingsService.getCurrentVersionOrDefault();
        targetVersion = settingsService.getTargetVersion();
        versionsToLoad = new LinkedList<>();

        deprecationStateIndex = new DeprecationStateIndex();
        compute(deprecationStateIndex);

        existenceStateIndex = new ExistenceStateIndex();
        compute(existenceStateIndex);

        apiCoverageStateIndex = new ApiCoverageStateIndex(existenceStateIndex.getIndexData());
        compute(apiCoverageStateIndex);
    }

    /**
     * Check if current instance is valid for settings.
     *
     * @param isSetIgnoreFlag boolean
     * @param currentVersion SupportedVersion
     * @param targetVersion SupportedVersion
     *
     * @return boolean
     */
    @SuppressWarnings("PMD.AvoidSynchronizedAtMethodLevel")
    private synchronized boolean isValidFor(
            final Boolean isSetIgnoreFlag,
            final SupportedVersion currentVersion,
            final SupportedVersion targetVersion
    ) {
        return this.isSetIgnoreFlag.equals(isSetIgnoreFlag)
                && this.currentVersion.equals(currentVersion)
                && this.targetVersion.equals(targetVersion);
    }

    /**
     * Compute index data.
     *
     * @param index VersionStateIndex
     */
    @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.AvoidDeeplyNestedIfStmts"})
    private void compute(final VersionStateIndex index) {
        if (targetVersion == null) {
            return;
        }

        if (versionsToLoad.isEmpty()) {
            for (final SupportedVersion version : SupportedVersion.values()) {
                if (version.compareTo(targetVersion) <= 0) {
                    if (isSetIgnoreFlag != null && isSetIgnoreFlag) {
                        // If current version is NULL, it is less than minimum supported version.
                        if (currentVersion == null || version.compareTo(currentVersion) > 0) {
                            versionsToLoad.add(version);
                        }
                    } else {
                        versionsToLoad.add(version);
                    }
                }
            }
        }

        index.load(versionsToLoad);
    }

    /**
     * Escape FQN for adding Factory and Proxy support.
     *
     * @param fqn String
     *
     * @return String
     */
    private String escapeFqn(final @NotNull String fqn) {
        return MagentoTypeEscapeUtil.escape(fqn);
    }
}
