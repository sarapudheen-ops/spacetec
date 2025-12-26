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
public class AnalysisProjectDependency extends DelegatingProjectDependency {

    @Inject
    public AnalysisProjectDependency(TypeSafeProjectDependencyFactory factory, ProjectDependencyInternal delegate) {
        super(factory, delegate);
    }

    /**
     * Creates a project dependency on the project at path ":analysis:core"
     */
    public Analysis_CoreProjectDependency getCore() { return new Analysis_CoreProjectDependency(getFactory(), create(":analysis:core")); }

    /**
     * Creates a project dependency on the project at path ":analysis:ml"
     */
    public Analysis_MlProjectDependency getMl() { return new Analysis_MlProjectDependency(getFactory(), create(":analysis:ml")); }

    /**
     * Creates a project dependency on the project at path ":analysis:patterns"
     */
    public Analysis_PatternsProjectDependency getPatterns() { return new Analysis_PatternsProjectDependency(getFactory(), create(":analysis:patterns")); }

    /**
     * Creates a project dependency on the project at path ":analysis:predictions"
     */
    public Analysis_PredictionsProjectDependency getPredictions() { return new Analysis_PredictionsProjectDependency(getFactory(), create(":analysis:predictions")); }

}
