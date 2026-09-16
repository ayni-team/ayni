/**
 * Public part of the shared library: the events modules exchange.
 *
 * <p>They live here so that neither the module that publishes an event nor the one that reacts to
 * it depends on the other. Both depend on this package.
 */
@org.springframework.modulith.NamedInterface("events")
package pe.ayni.shared.events;
