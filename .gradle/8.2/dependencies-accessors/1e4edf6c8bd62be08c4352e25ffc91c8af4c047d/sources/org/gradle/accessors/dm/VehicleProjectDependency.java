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
public class VehicleProjectDependency extends DelegatingProjectDependency {

    @Inject
    public VehicleProjectDependency(TypeSafeProjectDependencyFactory factory, ProjectDependencyInternal delegate) {
        super(factory, delegate);
    }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands"
     */
    public Vehicle_BrandsProjectDependency getBrands() { return new Vehicle_BrandsProjectDependency(getFactory(), create(":vehicle:brands")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:core"
     */
    public Vehicle_CoreProjectDependency getCore() { return new Vehicle_CoreProjectDependency(getFactory(), create(":vehicle:core")); }

}
