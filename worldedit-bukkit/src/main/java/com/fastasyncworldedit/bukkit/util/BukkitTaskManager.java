package com.fastasyncworldedit.bukkit.util;

import com.fastasyncworldedit.core.Fawe;
import com.fastasyncworldedit.core.util.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

import javax.annotation.Nonnull;
import java.util.function.Consumer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class BukkitTaskManager extends TaskManager {

    private final Plugin plugin;
    private final boolean isFolia;
    private final AtomicBoolean errorLogged = new AtomicBoolean(false);

    public BukkitTaskManager(final Plugin plugin) {
        this.plugin = plugin;
        // Detect if we're running on Folia
        boolean foliaDetected = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            foliaDetected = true;
        } catch (ClassNotFoundException e) {
            // Not Folia
        }
        this.isFolia = foliaDetected;
    }

    @Override
    public int repeat(@Nonnull final Runnable runnable, final int interval) {
        if (isFolia) {
            return repeatFolia(runnable, interval);
        } else {
            return Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    if (e instanceof IllegalStateException && e.getMessage().equals("Not main thread") && errorLogged.compareAndSet(false, true)) {
                        plugin.getLogger().log(Level.WARNING, "Error in FAWE task: Not main thread. This is a known issue with Folia, please report if you see this repeatedly.", e);
                    } else if (!(e instanceof IllegalStateException) || !e.getMessage().equals("Not main thread")) {
                        plugin.getLogger().log(Level.WARNING, "Error in FAWE task", e);
                    }
                }
            }, interval, interval);
        }
    }

    private int repeatFolia(@Nonnull final Runnable runnable, final int interval) {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            // Defer task until worlds are loaded
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onWorldLoad(WorldLoadEvent event) {
                    // Schedule the task now that a world is loaded
                    World world = event.getWorld();
                    try {
                        Location location = world.getSpawnLocation();
                        Consumer<Object> taskConsumer = task -> {
                            try {
                                runnable.run();
                            } catch (Exception e) {
                                if (e instanceof IllegalStateException && e.getMessage().equals("Not main thread") && errorLogged.compareAndSet(false, true)) {
                                    plugin.getLogger().log(Level.WARNING, "Error in FAWE task: Not main thread. This is a known issue with Folia, please report if you see this repeatedly.", e);
                                } else if (!(e instanceof IllegalStateException) || !e.getMessage().equals("Not main thread")) {
                                    plugin.getLogger().log(Level.WARNING, "Error in FAWE task", e);
                                }
                            }
                        };
                        
                        // Use global scheduler instead of region scheduler
                        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> taskConsumer.accept(task), interval, interval);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Error scheduling FAWE task in Folia", e);
                    }
                    
                    // Unregister the listener after scheduling
                    WorldLoadEvent.getHandlerList().unregister(this);
                }
            }, plugin);
        } else {
            World world = worlds.get(0); // Use the first world
            try {
                Location location = world.getSpawnLocation();
                Consumer<Object> taskConsumer = task -> {
                    try {
                        runnable.run();
                    } catch (Exception e) {
                        if (e instanceof IllegalStateException && e.getMessage().equals("Not main thread") && errorLogged.compareAndSet(false, true)) {
                            plugin.getLogger().log(Level.WARNING, "Error in FAWE task: Not main thread. This is a known issue with Folia, please report if you see this repeatedly.", e);
                        } else if (!(e instanceof IllegalStateException) || !e.getMessage().equals("Not main thread")) {
                            plugin.getLogger().log(Level.WARNING, "Error in FAWE task", e);
                        }
                    }
                };
                
                // Use global scheduler for tasks that need to run on main thread
                Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> taskConsumer.accept(task), interval, interval);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error scheduling FAWE task in Folia", e);
            }
        }
        return 0; // Adjust as needed
    }

    @Override
    public int repeatAsync(@Nonnull final Runnable runnable, final int interval) {
        if (isFolia) {
            // In Folia, we use the async scheduler directly
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error in FAWE async task", e);
                }
            }, interval * 50L, interval * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
            // Return a dummy task ID - Folia doesn't have a concept of task IDs for these schedulers
            return (int) (Math.random() * 10000) + 1;
        } else {
            // In regular Bukkit, use the standard async scheduler
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, interval, interval).getTaskId();
        }
    }

    @Override
    public void async(@Nonnull final Runnable runnable) {
        if (isFolia) {
            // In Folia, we use the async scheduler
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error in FAWE async task", e);
                }
            });
        } else {
            // In regular Bukkit, use the standard async scheduler
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    @Override
    public void task(@Nonnull final Runnable runnable) {
        if (isFolia) {
            // In Folia, use the global scheduler for tasks that need to run on the main thread
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    if (e instanceof IllegalStateException && e.getMessage().equals("Not main thread") && errorLogged.compareAndSet(false, true)) {
                        plugin.getLogger().log(Level.WARNING, "Error in FAWE task: Not main thread. This is a known issue with Folia, please report if you see this repeatedly.", e);
                    } else if (!(e instanceof IllegalStateException) || !e.getMessage().equals("Not main thread")) {
                        plugin.getLogger().log(Level.WARNING, "Error in FAWE task", e);
                    }
                }
            });
        } else {
            // In regular Bukkit, use the standard scheduler
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error in FAWE task", e);
                }
            });
        }
    }

    @Override
    public void later(@Nonnull final Runnable runnable, final int delay) {
        if (delay <= 0) {
            throw new IllegalArgumentException("Delay ticks must be greater than 0");
        }
        
        if (isFolia) {
            // In Folia, use the global scheduler for delayed tasks
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    if (e instanceof IllegalStateException && e.getMessage().equals("Not main thread") && errorLogged.compareAndSet(false, true)) {
                        plugin.getLogger().log(Level.WARNING, "Error in FAWE task: Not main thread. This is a known issue with Folia, please report if you see this repeatedly.", e);
                    } else if (!(e instanceof IllegalStateException) || !e.getMessage().equals("Not main thread")) {
                        plugin.getLogger().log(Level.WARNING, "Error in FAWE task", e);
                    }
                }
            }, delay);
        } else {
            // In regular Bukkit, use the standard scheduler
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delay);
        }
    }

    @Override
    public void laterAsync(@Nonnull final Runnable runnable, final int delay) {
        if (delay <= 0) {
            throw new IllegalArgumentException("Delay ticks must be greater than 0");
        }
        
        if (isFolia) {
            // In Folia, use the async scheduler for delayed async tasks
            Bukkit.getAsyncScheduler().runDelayed(plugin, task -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error in FAWE async task", e);
                }
            }, delay * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            // In regular Bukkit, use the standard async scheduler
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delay);
        }
    }

    @Override
    public void cancel(final int task) {
        if (isFolia) {
            // In Folia, individual task cancellation might not be fully supported
            // but we'll try to cancel anyway in case it's a standard task
            try {
                Bukkit.getScheduler().cancelTask(task);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error cancelling FAWE task in Folia", e);
            }
        } else {
            // In regular Bukkit, use the standard task cancellation
            Bukkit.getScheduler().cancelTask(task);
        }
    }

}
