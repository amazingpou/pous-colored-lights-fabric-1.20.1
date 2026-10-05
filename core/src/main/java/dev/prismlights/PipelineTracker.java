package dev.prismlights;

/** Tracks renderer resources by identity, including reloads while still enabled. */
public final class PipelineTracker {
    private boolean initialized, active;
    private Object manager, pipeline;
    private int generation;

    public boolean update(Object manager, Object pipeline, int generation, boolean active) {
        boolean changed=initialized && (this.manager!=manager || this.pipeline!=pipeline
            || this.generation!=generation || this.active!=active);
        initialized=true;
        this.manager=manager;
        this.pipeline=pipeline;
        this.generation=generation;
        this.active=active;
        return changed;
    }
}
