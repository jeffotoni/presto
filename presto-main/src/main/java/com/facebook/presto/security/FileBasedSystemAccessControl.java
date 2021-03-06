/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.security;

import com.facebook.presto.spi.CatalogSchemaName;
import com.facebook.presto.spi.CatalogSchemaTableName;
import com.facebook.presto.spi.SchemaTableName;
import com.facebook.presto.spi.security.Identity;
import com.facebook.presto.spi.security.Privilege;
import com.facebook.presto.spi.security.SystemAccessControl;
import com.facebook.presto.spi.security.SystemAccessControlFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static com.facebook.presto.spi.security.AccessDeniedException.denyCatalogAccess;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.util.Objects.requireNonNull;

public class FileBasedSystemAccessControl
        implements SystemAccessControl
{
    public static final String NAME = "file";

    private final List<CatalogAccessControlRule> catalogRules;

    private FileBasedSystemAccessControl(List<CatalogAccessControlRule> catalogRules)
    {
        this.catalogRules = catalogRules;
    }

    public static class Factory
            implements SystemAccessControlFactory
    {
        private static final String CONFIG_FILE_NAME = "security.config-file";

        @Override
        public String getName()
        {
            return NAME;
        }

        @Override
        public SystemAccessControl create(Map<String, String> config)
        {
            requireNonNull(config, "config is null");

            String configFileName = config.get(CONFIG_FILE_NAME);
            checkState(
                    configFileName != null,
                    "Security configuration must contain the '%s' property", CONFIG_FILE_NAME);

            try {
                Path path = Paths.get(configFileName);
                if (!path.isAbsolute()) {
                    path = path.toAbsolutePath();
                }
                path.toFile().canRead();

                ImmutableList.Builder<CatalogAccessControlRule> catalogRulesBuilder = ImmutableList.builder();
                catalogRulesBuilder.addAll(jsonCodec(FileBasedSystemAccessControlRules.class)
                        .fromJson(Files.readAllBytes(path))
                        .getCatalogRules());

                // Hack to allow Presto Admin to access the "system" catalog for retrieving server status.
                // todo Change userRegex from ".*" to one particular user that Presto Admin will be restricted to run as
                catalogRulesBuilder.add(new CatalogAccessControlRule(
                        true,
                        Optional.of(Pattern.compile(".*")),
                        Optional.of(Pattern.compile("system"))));

                return new FileBasedSystemAccessControl(catalogRulesBuilder.build());
            }
            catch (SecurityException | IOException | InvalidPathException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void checkCanSetUser(Principal principal, String userName)
    {
    }

    @Override
    public void checkCanSetSystemSessionProperty(Identity identity, String propertyName)
    {
    }

    @Override
    public void checkCanAccessCatalog(Identity identity, String catalogName)
    {
        if (!canAccessCatalog(identity, catalogName)) {
            denyCatalogAccess(catalogName);
        }
    }

    @Override
    public Set<String> filterCatalogs(Identity identity, Set<String> catalogs)
    {
        ImmutableSet.Builder<String> filteredCatalogs = ImmutableSet.builder();
        for (String catalog : catalogs) {
            if (canAccessCatalog(identity, catalog)) {
                filteredCatalogs.add(catalog);
            }
        }
        return filteredCatalogs.build();
    }

    private boolean canAccessCatalog(Identity identity, String catalogName)
    {
        for (CatalogAccessControlRule rule : catalogRules) {
            Optional<Boolean> allowed = rule.match(identity.getUser(), catalogName);
            if (allowed.isPresent()) {
                return allowed.get();
            }
        }
        return false;
    }

    @Override
    public void checkCanCreateSchema(Identity identity, CatalogSchemaName schema)
    {
    }

    @Override
    public void checkCanDropSchema(Identity identity, CatalogSchemaName schema)
    {
    }

    @Override
    public void checkCanRenameSchema(Identity identity, CatalogSchemaName schema, String newSchemaName)
    {
    }

    @Override
    public void checkCanShowSchemas(Identity identity, String catalogName)
    {
    }

    @Override
    public Set<String> filterSchemas(Identity identity, String catalogName, Set<String> schemaNames)
    {
        if (!canAccessCatalog(identity, catalogName)) {
            return ImmutableSet.of();
        }

        return schemaNames;
    }

    @Override
    public void checkCanCreateTable(Identity identity, CatalogSchemaTableName table)
    {
    }

    @Override
    public void checkCanDropTable(Identity identity, CatalogSchemaTableName table)
    {
    }

    @Override
    public void checkCanRenameTable(Identity identity, CatalogSchemaTableName table, CatalogSchemaTableName newTable)
    {
    }

    @Override
    public void checkCanShowTablesMetadata(Identity identity, CatalogSchemaName schema)
    {
    }

    @Override
    public Set<SchemaTableName> filterTables(Identity identity, String catalogName, Set<SchemaTableName> tableNames)
    {
        if (!canAccessCatalog(identity, catalogName)) {
            return ImmutableSet.of();
        }

        return tableNames;
    }

    @Override
    public void checkCanAddColumn(Identity identity, CatalogSchemaTableName table)
    {
    }

    @Override
    public void checkCanDropColumn(Identity identity, CatalogSchemaTableName table)
    {
    }

    @Override
    public void checkCanRenameColumn(Identity identity, CatalogSchemaTableName table)
    {
    }

    @Override
    public void checkCanSelectFromTable(Identity identity, CatalogSchemaTableName table)
    {
    }

    @Override
    public void checkCanInsertIntoTable(Identity identity, CatalogSchemaTableName table)
    {
    }

    @Override
    public void checkCanDeleteFromTable(Identity identity, CatalogSchemaTableName table)
    {
    }

    @Override
    public void checkCanCreateView(Identity identity, CatalogSchemaTableName view)
    {
    }

    @Override
    public void checkCanDropView(Identity identity, CatalogSchemaTableName view)
    {
    }

    @Override
    public void checkCanSelectFromView(Identity identity, CatalogSchemaTableName view)
    {
    }

    @Override
    public void checkCanCreateViewWithSelectFromTable(Identity identity, CatalogSchemaTableName table)
    {
    }

    @Override
    public void checkCanCreateViewWithSelectFromView(Identity identity, CatalogSchemaTableName view)
    {
    }

    @Override
    public void checkCanSetCatalogSessionProperty(Identity identity, String catalogName, String propertyName)
    {
    }

    @Override
    public void checkCanGrantTablePrivilege(Identity identity, Privilege privilege, CatalogSchemaTableName table, String grantee, boolean withGrantOption)
    {
    }

    @Override
    public void checkCanRevokeTablePrivilege(Identity identity, Privilege privilege, CatalogSchemaTableName table, String revokee, boolean grantOptionFor)
    {
    }
}
