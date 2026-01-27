package ru.playgame1435.easyvisuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * EasyVisuals - оптимизированные визуальные эффекты для Minecraft
 * Всё в одном файле для простоты сборки
 */
public class EasyVisuals implements ClientModInitializer {

	// === КОНФИГУРАЦИЯ ===
	public static ModConfig config;
	public static KeyBinding openGuiKey;
	public static ClickGUI clickGUI;

	// === ФУНКЦИИ ===
	private static final TrailsFunction trails = new TrailsFunction();
	private static final JumpCircles jumpCircles = new JumpCircles();
	private static final GlowOres glowOres = new GlowOres();

	// === ОСНОВНОЙ КЛАСС ===
	@Override
	public void onInitializeClient() {
		System.out.println("🚀 EasyVisuals загружается...");

		// Загрузка конфигурации
		config = ModConfig.load();

		// Регистрация горячей клавиши (Правый Shift)
		openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.easyvisuals.opengui",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				"category.easyvisuals.main"
		));

		// Создание GUI
		clickGUI = new ClickGUI();

		// Обработчик тиков
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Открытие GUI
			while (openGuiKey.wasPressed()) {
				client.setScreen(clickGUI);
			}

			// Обновление функций
			if (config.trailsEnabled) trails.onTick();
			if (config.jumpCirclesEnabled) jumpCircles.onTick();
			if (config.glowOresEnabled) glowOres.onTick();
		});

		// Рендеринг в мире
		WorldRenderEvents.END.register(context -> {
			MatrixStack matrices = context.matrixStack();
			float tickDelta = context.tickDelta();

			if (config.trailsEnabled) trails.render(matrices, tickDelta);
			if (config.glowOresEnabled) glowOres.render(matrices, tickDelta);
		});

		System.out.println("✅ EasyVisuals успешно загружен!");
	}

	// === КОНФИГУРАЦИЯ ===
	public static class ModConfig {
		// Trails
		public boolean trailsEnabled = true;
		public int trailsLength = 20;
		public float trailsOpacity = 0.7f;
		public int trailsColor = 0xFFFF00FF;
		public float trailsWidth = 0.5f;

		// Jump Circles
		public boolean jumpCirclesEnabled = true;
		public int jumpCircleColor = 0xFF00FFFF;
		public float jumpCircleRadius = 1.5f;
		public int jumpCircleDuration = 400;

		// Glow Ores
		public boolean glowOresEnabled = true;
		public float glowOresIntensity = 0.8f;
		public int glowOresColor = 0xFFFFAA00;
		public float glowOresRadius = 1.2f;
		public boolean glowOresThroughWalls = true;
		public Set<String> glowingBlocks = new HashSet<>();

		// GUI
		public boolean liquidGlassStyle = true;
		public float guiOpacity = 0.85f;
		public int guiAccentColor = 0xFF007AFF;

		// Оптимизация
		public boolean enableOptimization = true;
		public int maxParticles = 50;
		public boolean lowQualityMode = false;

		public ModConfig() {
			// Стандартные руды
			glowingBlocks.add("minecraft:diamond_ore");
			glowingBlocks.add("minecraft:deepslate_diamond_ore");
			glowingBlocks.add("minecraft:iron_ore");
			glowingBlocks.add("minecraft:deepslate_iron_ore");
			glowingBlocks.add("minecraft:gold_ore");
			glowingBlocks.add("minecraft:deepslate_gold_ore");
			glowingBlocks.add("minecraft:emerald_ore");
			glowingBlocks.add("minecraft:deepslate_emerald_ore");
			glowingBlocks.add("minecraft:coal_ore");
			glowingBlocks.add("minecraft:deepslate_coal_ore");
		}

		public void save() {
			try {
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				File configFile = new File("./config/easyvisuals.json");
				configFile.getParentFile().mkdirs();

				try (FileWriter writer = new FileWriter(configFile)) {
					gson.toJson(this, writer);
				}
			} catch (IOException e) {
				System.err.println("Не удалось сохранить конфигурацию: " + e.getMessage());
			}
		}

		public static ModConfig load() {
			try {
				Gson gson = new Gson();
				File configFile = new File("./config/easyvisuals.json");

				if (configFile.exists()) {
					try (FileReader reader = new FileReader(configFile)) {
						return gson.fromJson(reader, ModConfig.class);
					}
				}
			} catch (IOException e) {
				System.err.println("Не удалось загрузить конфигурацию: " + e.getMessage());
			}

			ModConfig newConfig = new ModConfig();
			newConfig.save();
			return newConfig;
		}
	}

	// === TRAILS FUNCTION ===
	private static class TrailsFunction {
		private final List<TrailPoint> trailPoints = new ArrayList<>();
		private Vec3d lastPosition = Vec3d.ZERO;

		private static class TrailPoint {
			final Vec3d position;
			final long timestamp;
			final float alpha;

			TrailPoint(Vec3d position, long timestamp, float alpha) {
				this.position = position;
				this.timestamp = timestamp;
				this.alpha = alpha;
			}
		}

		public void onTick() {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) return;

			Vec3d currentPos = client.player.getPos();

			if (!currentPos.equals(lastPosition)) {
				trailPoints.add(new TrailPoint(
						currentPos,
						System.currentTimeMillis(),
						config.trailsOpacity
				));
				lastPosition = currentPos;
			}

			// Очистка старых точек
			long currentTime = System.currentTimeMillis();
			trailPoints.removeIf(point ->
					currentTime - point.timestamp > config.trailsLength * 50L
			);

			// Ограничение количества
			while (trailPoints.size() > config.trailsLength) {
				trailPoints.remove(0);
			}
		}

		public void render(MatrixStack matrices, float tickDelta) {
			if (trailPoints.size() < 2) return;

			MinecraftClient client = MinecraftClient.getInstance();
			Vec3d cameraPos = client.gameRenderer.getCamera().getPos();

			Tessellator tessellator = Tessellator.getInstance();
			BufferBuilder buffer = tessellator.begin(
					VertexFormat.DrawMode.DEBUG_LINES,
					VertexFormats.POSITION_COLOR
			);

			Matrix4f matrix = matrices.peek().getPositionMatrix();
			long currentTime = System.currentTimeMillis();
			int color = config.trailsColor;

			int r = (color >> 16) & 0xFF;
			int g = (color >> 8) & 0xFF;
			int b = color & 0xFF;

			for (int i = 0; i < trailPoints.size() - 1; i++) {
				TrailPoint p1 = trailPoints.get(i);
				TrailPoint p2 = trailPoints.get(i + 1);

				float age1 = (currentTime - p1.timestamp) / 1000.0f;
				float age2 = (currentTime - p2.timestamp) / 1000.0f;

				int a1 = (int)(p1.alpha * (1.0f - age1 / 10.0f) * 255);
				int a2 = (int)(p2.alpha * (1.0f - age2 / 10.0f) * 255);

				float x1 = (float)(p1.position.x - cameraPos.x);
				float y1 = (float)(p1.position.y - cameraPos.y) + 0.1f;
				float z1 = (float)(p1.position.z - cameraPos.z);

				float x2 = (float)(p2.position.x - cameraPos.x);
				float y2 = (float)(p2.position.y - cameraPos.y) + 0.1f;
				float z2 = (float)(p2.position.z - cameraPos.z);

				buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a1).next();
				buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a2).next();
			}

			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableDepthTest();
			RenderSystem.lineWidth(config.trailsWidth * 2.0f);

			tessellator.draw();

			RenderSystem.enableDepthTest();
			RenderSystem.lineWidth(1.0f);
		}

		public int getTrailPointCount() {
			return trailPoints.size();
		}
	}

	// === JUMP CIRCLES FUNCTION ===
	private static class JumpCircles {
		private final List<JumpCircle> activeCircles = new ArrayList<>();
		private boolean wasOnGround = true;

		private static class JumpCircle {
			final Vec3d center;
			final long startTime;

			JumpCircle(Vec3d center, long startTime) {
				this.center = center;
				this.startTime = startTime;
			}

			float getProgress(long currentTime) {
				long elapsed = currentTime - startTime;
				return Math.min(elapsed / (float)config.jumpCircleDuration, 1.0f);
			}

			boolean isExpired(long currentTime) {
				long elapsed = currentTime - startTime;
				return elapsed > config.jumpCircleDuration;
			}
		}

		public void onTick() {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) return;

			boolean isOnGround = client.player.isOnGround();
			long currentTime = System.currentTimeMillis();

			// Обнаружение прыжка
			if (!wasOnGround && isOnGround) {
				// Приземление
				activeCircles.add(new JumpCircle(client.player.getPos(), currentTime));
			} else if (wasOnGround && !isOnGround && client.player.getVelocity().y > 0) {
				// Прыжок
				activeCircles.add(new JumpCircle(client.player.getPos(), currentTime));
			}

			wasOnGround = isOnGround;

			// Удаление старых кругов
			activeCircles.removeIf(circle -> circle.isExpired(currentTime));
		}

		public int getActiveCircleCount() {
			return activeCircles.size();
		}
	}

	// === GLOW ORES FUNCTION ===
	private static class GlowOres {
		private final Map<BlockPos, Long> glowingBlocks = new HashMap<>();
		private int lastScanTick = 0;

		public void onTick() {
			if (!config.glowOresEnabled) {
				glowingBlocks.clear();
				return;
			}

			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) return;

			// Сканируем каждые 20 тиков (1 секунда)
			if (client.world.getTime() - lastScanTick > 20) {
				scanForOres();
				lastScanTick = (int)client.world.getTime();
			}

			// Очистка
			long currentTime = System.currentTimeMillis();
			glowingBlocks.entrySet().removeIf(entry ->
					currentTime - entry.getValue() > 5000
			);
		}

		private void scanForOres() {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.world == null) return;

			BlockPos playerPos = client.player.getBlockPos();
			int radius = 12;

			Map<BlockPos, Long> newBlocks = new HashMap<>();

			// Простой поиск (в реальности нужна проверка блоков)
			for (int x = -radius; x <= radius; x++) {
				for (int y = -radius; y <= radius; y++) {
					for (int z = -radius; z <= radius; z++) {
						BlockPos pos = playerPos.add(x, y, z);
						// Здесь должна быть проверка типа блока
						// Для примепа добавляем случайные позиции
						if (Math.random() < 0.01) {
							newBlocks.put(pos, System.currentTimeMillis());
						}
					}
				}
			}

			glowingBlocks.putAll(newBlocks);

			// Ограничение количества
			if (glowingBlocks.size() > 100) {
				// Оставляем только ближайшие
				List<Map.Entry<BlockPos, Long>> sorted = new ArrayList<>(glowingBlocks.entrySet());
				sorted.sort(Comparator.comparingDouble(
						entry -> client.player.getPos().distanceTo(
								new Vec3d(entry.getKey().getX(), entry.getKey().getY(), entry.getKey().getZ())
						)
				));

				glowingBlocks.clear();
				int limit = Math.min(50, sorted.size());
				for (int i = 0; i < limit; i++) {
					glowingBlocks.put(sorted.get(i).getKey(), sorted.get(i).getValue());
				}
			}
		}

		public void render(MatrixStack matrices, float tickDelta) {
			if (glowingBlocks.isEmpty()) return;

			MinecraftClient client = MinecraftClient.getInstance();
			Vec3d cameraPos = client.gameRenderer.getCamera().getPos();

			Tessellator tessellator = Tessellator.getInstance();
			BufferBuilder buffer = tessellator.begin(
					VertexFormat.DrawMode.QUADS,
					VertexFormats.POSITION_COLOR
			);

			Matrix4f matrix = matrices.peek().getPositionMatrix();
			long currentTime = System.currentTimeMillis();
			int color = config.glowOresColor;
			float pulse = (float)Math.sin(currentTime * 0.001) * 0.1f + 0.9f;

			int r = (color >> 16) & 0xFF;
			int g = (color >> 8) & 0xFF;
			int b = color & 0xFF;

			for (Map.Entry<BlockPos, Long> entry : glowingBlocks.entrySet()) {
				BlockPos pos = entry.getKey();
				long age = currentTime - entry.getValue();
				float alpha = Math.min(age / 500.0f, 1.0f) * config.glowOresIntensity * pulse;
				int a = (int)(alpha * 255);

				float x = pos.getX() - 0.5f;
				float y = pos.getY() - 0.5f;
				float z = pos.getZ() - 0.5f;
				float size = config.glowOresRadius;

				// Простой куб
				drawCube(buffer, matrix, x, y, z, size, r, g, b, a);
			}

			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableDepthTest();

			tessellator.draw();

			RenderSystem.enableDepthTest();
		}

		private void drawCube(BufferBuilder buffer, Matrix4f matrix,
							  float x, float y, float z, float size,
							  int r, int g, int b, int a) {
			float x1 = x - size;
			float y1 = y - size;
			float z1 = z - size;
			float x2 = x + 1 + size;
			float y2 = y + 1 + size;
			float z2 = z + 1 + size;

			// 6 граней куба
			drawQuad(buffer, matrix, x1, y1, z1, x2, y1, z2, r, g, b, a); // Низ
			drawQuad(buffer, matrix, x1, y2, z1, x2, y2, z2, r, g, b, a); // Верх
			drawQuad(buffer, matrix, x1, y1, z1, x2, y2, z1, r, g, b, a); // Зад
			drawQuad(buffer, matrix, x1, y1, z2, x2, y2, z2, r, g, b, a); // Перед
			drawQuad(buffer, matrix, x1, y1, z1, x1, y2, z2, r, g, b, a); // Лево
			drawQuad(buffer, matrix, x2, y1, z1, x2, y2, z2, r, g, b, a); // Право
		}

		private void drawQuad(BufferBuilder buffer, Matrix4f matrix,
							  float x1, float y1, float z1,
							  float x2, float y2, float z2,
							  int r, int g, int b, int a) {
			buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).next();
			buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a).next();
			buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).next();
			buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a).next();
		}

		public int getGlowingBlockCount() {
			return glowingBlocks.size();
		}
	}

	// === CLICK GUI ===
	private static class ClickGUI extends Screen {
		private ButtonWidget trailsToggle;
		private ButtonWidget jumpCirclesToggle;
		private ButtonWidget glowOresToggle;
		private ButtonWidget saveButton;

		public ClickGUI() {
			super(Text.literal("EasyVisuals Settings"));
		}

		@Override
		protected void init() {
			super.init();

			int centerX = this.width / 2 - 75;
			int startY = this.height / 4;
			int padding = 25;

			// Trails Toggle
			trailsToggle = ButtonWidget.builder(
					getToggleText("Trails", config.trailsEnabled),
					button -> {
						config.trailsEnabled = !config.trailsEnabled;
						trailsToggle.setMessage(getToggleText("Trails", config.trailsEnabled));
					}
			).dimensions(centerX, startY, 150, 20).build();

			// Jump Circles Toggle
			jumpCirclesToggle = ButtonWidget.builder(
					getToggleText("Jump Circles", config.jumpCirclesEnabled),
					button -> {
						config.jumpCirclesEnabled = !config.jumpCirclesEnabled;
						jumpCirclesToggle.setMessage(getToggleText("Jump Circles", config.jumpCirclesEnabled));
					}
			).dimensions(centerX, startY + padding, 150, 20).build();

			// Glow Ores Toggle
			glowOresToggle = ButtonWidget.builder(
					getToggleText("Glow Ores", config.glowOresEnabled),
					button -> {
						config.glowOresEnabled = !config.glowOresEnabled;
						glowOresToggle.setMessage(getToggleText("Glow Ores", config.glowOresEnabled));
					}
			).dimensions(centerX, startY + padding * 2, 150, 20).build();

			// Save Button
			saveButton = ButtonWidget.builder(
					Text.literal("Save & Close").formatted(Formatting.GREEN),
					button -> {
						config.save();
						this.client.setScreen(null);
					}
			).dimensions(centerX, startY + padding * 4, 150, 20).build();

			// Добавление виджетов
			this.addDrawableChild(trailsToggle);
			this.addDrawableChild(jumpCirclesToggle);
			this.addDrawableChild(glowOresToggle);
			this.addDrawableChild(saveButton);
		}

		@Override
		public void render(DrawContext context, int mouseX, int mouseY, float delta) {
			// Фон
			this.renderBackground(context, mouseX, mouseY, delta);

			// Заголовок
			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal("EasyVisuals").formatted(Formatting.BOLD, Formatting.AQUA),
					this.width / 2,
					20,
					0xFFFFFF
			);

			// Подзаголовок
			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal("Оптимизированные визуальные эффекты").formatted(Formatting.GRAY),
					this.width / 2,
					35,
					0xFFFFFF
			);

			// Статистика
			String stats = String.format("Trails: %d | Circles: %d | Ores: %d",
					trails.getTrailPointCount(),
					jumpCircles.getActiveCircleCount(),
					glowOres.getGlowingBlockCount());

			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal(stats).formatted(Formatting.GRAY),
					this.width / 2,
					this.height - 30,
					0xFFFFFF
			);

			super.render(context, mouseX, mouseY, delta);
		}

		private Text getToggleText(String feature, boolean enabled) {
			Formatting color = enabled ? Formatting.GREEN : Formatting.RED;
			String status = enabled ? "ON" : "OFF";
			return Text.literal(feature + ": ").append(Text.literal(status).formatted(color));
		}

		@Override
		public boolean shouldCloseOnEsc() {
			return true;
		}
	}
}