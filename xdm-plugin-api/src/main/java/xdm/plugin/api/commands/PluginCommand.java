package xdm.plugin.api.commands;

import xdm.plugin.api.PluginContext;

public interface PluginCommand {
    String getName();
    String getDescription();
    void execute(String[] args, PluginContext context) throws Exception;
}

