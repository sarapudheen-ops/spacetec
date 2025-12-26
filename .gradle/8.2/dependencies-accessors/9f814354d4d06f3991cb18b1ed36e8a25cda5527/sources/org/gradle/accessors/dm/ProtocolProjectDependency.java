package org.gradle.accessors.dm;

import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.catalog.DelegatingProjectDependency;
import org.gradle.api.internal.catalog.TypeSafeProjectDependencyFactory;
import javax.inject.Inject;

@NonNullApi
public class ProtocolProjectDependency extends DelegatingProjectDependency {

    @Inject
    public ProtocolProjectDependency(TypeSafeProjectDependencyFactory factory, ProjectDependencyInternal delegate) {
        super(factory, delegate);
    }

    /**
     * Creates a project dependency on the project at path ":protocol:iso14230"
     */
    public Protocol_Iso14230ProjectDependency getIso14230() { return new Protocol_Iso14230ProjectDependency(getFactory(), create(":protocol:iso14230")); }

    /**
     * Creates a project dependency on the project at path ":protocol:iso9141"
     */
    public Protocol_Iso9141ProjectDependency getIso9141() { return new Protocol_Iso9141ProjectDependency(getFactory(), create(":protocol:iso9141")); }

}
