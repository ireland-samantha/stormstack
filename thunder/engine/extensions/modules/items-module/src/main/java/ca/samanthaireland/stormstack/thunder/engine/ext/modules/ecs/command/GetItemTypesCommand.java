package ca.samanthaireland.stormstack.thunder.engine.ext.modules.ecs.command;

import ca.samanthaireland.stormstack.thunder.engine.core.command.CommandBuilder;
import ca.samanthaireland.stormstack.thunder.engine.core.command.EngineCommand;
import ca.samanthaireland.stormstack.thunder.engine.core.command.PayloadMapper;
import ca.samanthaireland.stormstack.thunder.engine.ext.modules.domain.service.ItemTypeService;
import ca.samanthaireland.stormstack.thunder.engine.ext.modules.ecs.command.dto.GetItemTypesPayload;

import java.util.Map;

/**
 * Command to get all item types (for debugging).
 */
public final class GetItemTypesCommand {

    private GetItemTypesCommand() {}

    /**
     * Creates a new getItemTypes command.
     *
     * @param itemTypeService the item type service
     * @return the configured EngineCommand
     */
    public static EngineCommand create(ItemTypeService itemTypeService) {
        return CommandBuilder.newCommand()
                .withName("getItemTypes")
                .withSchema(Map.of("matchId", Long.class))
                .withExecution(payload -> {
                    GetItemTypesPayload dto = PayloadMapper.convert(payload, GetItemTypesPayload.class);
                    itemTypeService.getAllItemTypes(dto.getMatchId());
                })
                .build();
    }
}
