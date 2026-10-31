/**
 * Public part of the shared library: who the demonstration is told with, and the steps of its
 * history.
 *
 * <p>{@link pe.ayni.shared.demo.DemoCast} names the people every module's {@code Demo*Data} seeds,
 * so a fixed identifier is written once. The ports are what the history of the {@code demo} profile
 * asks of each module: each module implements its own, under that profile only, with its own use
 * cases, and the history in {@code config.demo} only says in what order and at what time. That is
 * how the history crosses every module without any of them reaching into another (docs/demo.md,
 * "The history").
 */
@org.springframework.modulith.NamedInterface("demo")
package pe.ayni.shared.demo;
