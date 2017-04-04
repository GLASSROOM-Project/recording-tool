package de.glassroom.grt;

/**
 * Created by Schwantzer on 30.09.2016.
 */
public interface TaskCompleteHandler {
    public final Long SUCCESS = 0l;
    public final Long ERROR = 1l;
    public final Long CANCELLED = 2l;

    public void taskComplete(Long result);
}
