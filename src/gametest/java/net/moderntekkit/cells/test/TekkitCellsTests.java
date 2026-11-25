package net.moderntekkit.cells.test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers Tekkit Cells' GameTests (run with {@code ./gradlew runGameTestServer}). Each test is a {@link CellTest}
 * instance naming a method in {@link CellGameTests}; they all use the empty 5x3x5 structure
 * tekkitcellstests:empty.
 */
@Mod(TekkitCellsTests.MOD_ID)
public final class TekkitCellsTests {
    public static final String MOD_ID = "tekkitcellstests";

    static final DeferredRegister<MapCodec<? extends GameTestInstance>> TYPES = DeferredRegister
            .create(Registries.TEST_INSTANCE_TYPE, MOD_ID);

    static {
        TYPES.register("cell_test", () -> CellTest.CODEC);
    }

    static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();

    static {
        TESTS.put("cell_32m_in_drive", CellGameTests::cell32mInDrive);
        TESTS.put("fluid_cell_32m_in_drive", CellGameTests::fluidCell32mInDrive);
        TESTS.put("all_tiers_capacity", CellGameTests::allTiersCapacity);
        TESTS.put("recipes_load", CellGameTests::recipesLoad);
    }

    public TekkitCellsTests(IEventBus modBus) {
        TYPES.register(modBus);
        modBus.addListener(this::registerTests);
    }

    private void registerTests(RegisterGameTestsEvent event) {
        for (var name : TESTS.keySet()) {
            Holder<TestEnvironmentDefinition<?>> environment = Holder.direct(new TestEnvironmentDefinition.AllOf());
            var data = new TestData<>(environment, Level.OVERWORLD, Identifier.fromNamespaceAndPath(MOD_ID, "empty"),
                    200, 0, true, Rotation.NONE, false, 1, 1, false, 0);
            event.registerTest(Identifier.fromNamespaceAndPath(MOD_ID, name), new CellTest(data, name));
        }
    }

    static final class CellTest extends GameTestInstance {
        static final MapCodec<CellTest> CODEC = RecordCodecBuilder.mapCodec(b -> b.group(
                TestData.CODEC.fieldOf("testData").forGetter((CellTest t) -> t.info()),
                Codec.STRING.fieldOf("name").forGetter(t -> t.name)).apply(b, CellTest::new));

        private final String name;

        CellTest(TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
            super(data);
            this.name = name;
        }

        @Override
        public void run(GameTestHelper helper) {
            TESTS.get(name).accept(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Tekkit Cells test " + name);
        }
    }
}
