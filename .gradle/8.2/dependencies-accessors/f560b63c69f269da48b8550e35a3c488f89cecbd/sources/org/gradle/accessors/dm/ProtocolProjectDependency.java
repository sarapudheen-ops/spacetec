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
     * Creates a project dependency on the project at path ":protocol:can"
     */
    public Protocol_CanProjectDependency getCan() { return new Protocol_CanProjectDependency(getFactory(), create(":protocol:can")); }

    /**
     * Creates a project dependency on the project at path ":protocol:core"
     */
    public Protocol_CoreProjectDependency getCore() { return new Protocol_CoreProjectDependency(getFactory(), create(":protocol:core")); }

    /**
     * Creates a project dependency on the project at path ":protocol:j1850"
     */
    public Protocol_J1850ProjectDependency getJ1850() { return new Protocol_J1850ProjectDependency(getFactory(), create(":protocol:j1850")); }

    /**
     * Creates a project dependency on the project at path ":protocol:kline"
     */
    public Protocol_KlineProjectDependency getKline() { return new Protocol_KlineProjectDependency(getFactory(), create(":protocol:kline")); }

    /**
     * Creates a project dependency on the project at path ":protocol:obd"
     */
    public Protocol_ObdProjectDependency getObd() { return new Protocol_ObdProjectDependency(getFactory(), create(":protocol:obd")); }

    /**
     * Creates a project dependency on the project at path ":protocol:safety"
     */
    public Protocol_SafetyProjectDependency getSafety() { return new Protocol_SafetyProjectDependency(getFactory(), create(":protocol:safety")); }

    /**
     * Creates a project dependency on the project at path ":protocol:security"
     */
    public Protocol_SecurityProjectDependency getSecurity() { return new Protocol_SecurityProjectDependency(getFactory(), create(":protocol:security")); }

    /**
     * Creates a project dependency on the project at path ":protocol:uds"
     */
    public Protocol_UdsProjectDependency getUds() { return new Protocol_UdsProjectDependency(getFactory(), create(":protocol:uds")); }

}
