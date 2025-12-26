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
public class Vehicle_BrandsProjectDependency extends DelegatingProjectDependency {

    @Inject
    public Vehicle_BrandsProjectDependency(TypeSafeProjectDependencyFactory factory, ProjectDependencyInternal delegate) {
        super(factory, delegate);
    }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:audi"
     */
    public Vehicle_Brands_AudiProjectDependency getAudi() { return new Vehicle_Brands_AudiProjectDependency(getFactory(), create(":vehicle:brands:audi")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:bmw"
     */
    public Vehicle_Brands_BmwProjectDependency getBmw() { return new Vehicle_Brands_BmwProjectDependency(getFactory(), create(":vehicle:brands:bmw")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:chevrolet"
     */
    public Vehicle_Brands_ChevroletProjectDependency getChevrolet() { return new Vehicle_Brands_ChevroletProjectDependency(getFactory(), create(":vehicle:brands:chevrolet")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:chrysler"
     */
    public Vehicle_Brands_ChryslerProjectDependency getChrysler() { return new Vehicle_Brands_ChryslerProjectDependency(getFactory(), create(":vehicle:brands:chrysler")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:dodge"
     */
    public Vehicle_Brands_DodgeProjectDependency getDodge() { return new Vehicle_Brands_DodgeProjectDependency(getFactory(), create(":vehicle:brands:dodge")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:ford"
     */
    public Vehicle_Brands_FordProjectDependency getFord() { return new Vehicle_Brands_FordProjectDependency(getFactory(), create(":vehicle:brands:ford")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:generic"
     */
    public Vehicle_Brands_GenericProjectDependency getGeneric() { return new Vehicle_Brands_GenericProjectDependency(getFactory(), create(":vehicle:brands:generic")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:gm"
     */
    public Vehicle_Brands_GmProjectDependency getGm() { return new Vehicle_Brands_GmProjectDependency(getFactory(), create(":vehicle:brands:gm")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:honda"
     */
    public Vehicle_Brands_HondaProjectDependency getHonda() { return new Vehicle_Brands_HondaProjectDependency(getFactory(), create(":vehicle:brands:honda")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:hyundai"
     */
    public Vehicle_Brands_HyundaiProjectDependency getHyundai() { return new Vehicle_Brands_HyundaiProjectDependency(getFactory(), create(":vehicle:brands:hyundai")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:jaguar"
     */
    public Vehicle_Brands_JaguarProjectDependency getJaguar() { return new Vehicle_Brands_JaguarProjectDependency(getFactory(), create(":vehicle:brands:jaguar")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:jeep"
     */
    public Vehicle_Brands_JeepProjectDependency getJeep() { return new Vehicle_Brands_JeepProjectDependency(getFactory(), create(":vehicle:brands:jeep")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:kia"
     */
    public Vehicle_Brands_KiaProjectDependency getKia() { return new Vehicle_Brands_KiaProjectDependency(getFactory(), create(":vehicle:brands:kia")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:landrover"
     */
    public Vehicle_Brands_LandroverProjectDependency getLandrover() { return new Vehicle_Brands_LandroverProjectDependency(getFactory(), create(":vehicle:brands:landrover")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:lexus"
     */
    public Vehicle_Brands_LexusProjectDependency getLexus() { return new Vehicle_Brands_LexusProjectDependency(getFactory(), create(":vehicle:brands:lexus")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:mazda"
     */
    public Vehicle_Brands_MazdaProjectDependency getMazda() { return new Vehicle_Brands_MazdaProjectDependency(getFactory(), create(":vehicle:brands:mazda")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:mercedes"
     */
    public Vehicle_Brands_MercedesProjectDependency getMercedes() { return new Vehicle_Brands_MercedesProjectDependency(getFactory(), create(":vehicle:brands:mercedes")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:nissan"
     */
    public Vehicle_Brands_NissanProjectDependency getNissan() { return new Vehicle_Brands_NissanProjectDependency(getFactory(), create(":vehicle:brands:nissan")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:porsche"
     */
    public Vehicle_Brands_PorscheProjectDependency getPorsche() { return new Vehicle_Brands_PorscheProjectDependency(getFactory(), create(":vehicle:brands:porsche")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:subaru"
     */
    public Vehicle_Brands_SubaruProjectDependency getSubaru() { return new Vehicle_Brands_SubaruProjectDependency(getFactory(), create(":vehicle:brands:subaru")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:toyota"
     */
    public Vehicle_Brands_ToyotaProjectDependency getToyota() { return new Vehicle_Brands_ToyotaProjectDependency(getFactory(), create(":vehicle:brands:toyota")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:volkswagen"
     */
    public Vehicle_Brands_VolkswagenProjectDependency getVolkswagen() { return new Vehicle_Brands_VolkswagenProjectDependency(getFactory(), create(":vehicle:brands:volkswagen")); }

    /**
     * Creates a project dependency on the project at path ":vehicle:brands:volvo"
     */
    public Vehicle_Brands_VolvoProjectDependency getVolvo() { return new Vehicle_Brands_VolvoProjectDependency(getFactory(), create(":vehicle:brands:volvo")); }

}
