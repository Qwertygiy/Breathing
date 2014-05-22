/*
 * Copyright 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.breathing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.breathing.component.DrowningComponent;
import org.terasology.breathing.component.DrownsComponent;
import org.terasology.engine.Time;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.characters.CharacterMovementComponent;
import org.terasology.logic.characters.events.OnEnterBlockEvent;
import org.terasology.logic.health.DoDamageEvent;
import org.terasology.logic.health.EngineDamageTypes;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.Vector3i;
import org.terasology.registry.CoreRegistry;
import org.terasology.registry.In;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;

/**
 * @author Immortius
 */
@RegisterSystem(RegisterMode.AUTHORITY)
public class DrowningSystem extends BaseComponentSystem implements UpdateSubscriberSystem {
    private static final Logger logger = LoggerFactory.getLogger(DrowningSystem.class);
    // TODO on player spawn, attach the drowning component

    @In
    private BlockEntityRegistry blockEntityProvider;

    @In
    private Time time;

    @In
    private EntityManager entityManager;

    @In
    private WorldProvider worldProvider;

    @Override
    public void update(float delta) {
        for (EntityRef entity : entityManager.getEntitiesWith(DrowningComponent.class, DrownsComponent.class, LocationComponent.class)) {
            DrowningComponent drowning = entity.getComponent(DrowningComponent.class);
            LocationComponent loc = entity.getComponent(LocationComponent.class);
            DrownsComponent drowns = entity.getComponent(DrownsComponent.class);

            long gameTime = time.getGameTimeInMs();
            // check for out of breath and full breath conditions
            if (drowning.isBreathing && gameTime > drowning.endTime) {
                // clean up the org.terasology.breathing component
                entity.removeComponent(DrowningComponent.class);
            } else {
                if (gameTime > drowning.nextDrownDamageTime) {
                    // damage the entity
                    EntityRef liquidBlock = blockEntityProvider.getBlockEntityAt(loc.getWorldPosition());
                    entity.send(new DoDamageEvent(drowns.drownDamage, EngineDamageTypes.DROWNING.get(), liquidBlock));
                    // set the next damage time
                    drowning.nextDrownDamageTime = gameTime + drowns.timeBetweenDrownDamage;
                    entity.saveComponent(drowning);
                }
            }
        }
    }

    @ReceiveEvent(components = {DrownsComponent.class})
    public void onEnterBlock(OnEnterBlockEvent event, EntityRef entity, DrownsComponent drowns) {
        // only trigger org.terasology.breathing if liquid is covering the top of the character (aka,  the head)
        if (isHeadLevel(event.getCharacterRelativePosition(), entity)) {
            DrowningComponent drowning = entity.getComponent(DrowningComponent.class);
            if (!blockIsBreathable(entity, event.getNewBlock())) {
                if (drowning != null) {
                    if (drowning.isBreathing) {
                        setBreathing(false, drowning, drowns);
                        entity.saveComponent(drowning);
                    }
                } else {
                    drowning = new DrowningComponent();
                    setBreathing(false, drowning, drowns);
                    entity.addComponent(drowning);
                }
            } else {
                if (drowning != null && !drowning.isBreathing) {
                    setBreathing(true, drowning, drowns);
                    entity.saveComponent(drowning);
                }
            }
        }
    }

    private boolean isHeadLevel(Vector3i relativePosition, EntityRef entity) {
        CharacterMovementComponent characterMovementComponent = entity.getComponent(CharacterMovementComponent.class);
        return (int) Math.ceil(characterMovementComponent.height) - 1 == relativePosition.y;
    }

    private boolean blockIsBreathable(EntityRef entity, Block block) {
        // TODO Once blocks become prefabs/entities, check to see if entity can breathe any of the block's elements
        // This is simply a placeholder for now
        DrownsComponent drowns = entity.getComponent(DrownsComponent.class);
        BlockManager blockManager = CoreRegistry.get(BlockManager.class);

        for (int i = 0; i < drowns.breathes.size(); i++) {
            String breathableMaterial = drowns.breathes.get(i);

            if (breathableMaterial.equalsIgnoreCase("Oxygen") && block.equals(BlockManager.getAir()) ||
                breathableMaterial.equalsIgnoreCase("Water") && block.equals(blockManager.getBlock("core:water"))) {

                return true;
            }
        }
        return false;
    }

    private void setBreathing(boolean isBreathing, DrowningComponent drowning, DrownsComponent drowns) {
        long gameTime = time.getGameTimeInMs();
        // if this is a new org.terasology.breathing component, set to 0% so that it starts from the current time
        float currentPercentage = drowning.startTime != 0 ? drowning.getRemainingBreath(gameTime) : 1f;
        if (!isBreathing) {
            currentPercentage = 1f - currentPercentage;
        }
        float scale = isBreathing ? drowns.breathRechargeRate : 1.0f;

        drowning.isBreathing = isBreathing;
        drowning.startTime = gameTime - (int) (drowns.breathCapacity * currentPercentage / scale);
        drowning.endTime = gameTime + (int) (drowns.breathCapacity * (1f - currentPercentage) / scale);

        if (isBreathing) {
            drowning.nextDrownDamageTime = Long.MAX_VALUE;
        } else {
            drowning.nextDrownDamageTime = drowning.endTime + drowns.timeBetweenDrownDamage;
        }
    }
}
