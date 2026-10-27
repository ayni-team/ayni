/**
 * Public part of the shared library: who a request belongs to, and to which university.
 *
 * <p>Spring Modulith treats every package under {@code pe.ayni} as a module, and only what a module
 * declares as a named interface may be used from outside it. Without this declaration, a class here
 * is an internal detail of {@code shared} and the build fails the moment another module uses it.
 */
@org.springframework.modulith.NamedInterface("tenancy")
package pe.ayni.shared.tenancy;
