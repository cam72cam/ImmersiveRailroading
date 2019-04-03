package cam72cam.immersiverailroading.gui.overlay;

import org.lwjgl.opengl.GL11;

import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.entity.HandCar;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;

public class LocomotiveOverlay extends Gui {
	
	protected Minecraft mc;
	private int screenWidth;
	private int screenHeight;
	private int currPosX;
	private int currPosY;
	private int currSpeedPosX;
	private int bgPosX;
	private int bgPosY;
	
	private static final int gaugeWidth = 10;
	private static final int gaugeHeight = 50;
	private static final int gaugeSpacer = 10;
	
	private static final int scalarWidth = 10;
	private static final int scalarHeight = 50;
	private static final int scalarSpacer = 10;
	
	public static final ResourceLocation OVERLAY_STEAM_TEXTURE = new ResourceLocation("immersiverailroading:gui/overlay_steam.png");
	public static final ResourceLocation OVERLAY_DIESEL_TEXTURE = new ResourceLocation("immersiverailroading:gui/overlay_diesel.png");
	public static final ResourceLocation OVERLAY_HANDCAR_TEXTURE = new ResourceLocation("immersiverailroading:gui/overlay_handcar.png");
	
	/*private static final int textHeight = 20;
	private static final int textVerticalSpacing = 5;*/

	public LocomotiveOverlay() {
		mc = Minecraft.getMinecraft();
		ScaledResolution scaled = new ScaledResolution(mc);
		screenWidth = scaled.getScaledWidth();
		screenHeight = scaled.getScaledHeight();
		
		currPosX = (int) (screenWidth * (ConfigGraphics.GUIPositionHorizontal/100f));
		currSpeedPosX = (int) (screenWidth * (ConfigGraphics.GUIPositionHorizontal/100f));
		bgPosX = (int) (screenWidth * (ConfigGraphics.GUIPositionHorizontal/100f)) - 5;
		currPosY = (int) (screenHeight * (ConfigGraphics.GUIPositionVertical/100f));
		currPosY -= 50;
		bgPosY = (int) (screenHeight * (ConfigGraphics.GUIPositionVertical/100f)) - gaugeHeight - 25;
	}

	public void drawGauge(int color, float liquidAmount, float tankCapacity, String units) {
		String amount = String.format("%.1f%s", liquidAmount, units);
		String capacity = String.format("%.1f%s", tankCapacity, units);
		//drawRect(currPosX, currPosY, currPosX + gaugeWidth, currPosY + gaugeHeight, 0xFF4d4d4d);
		int quantHeight = (int)(gaugeHeight * (liquidAmount / tankCapacity));
		drawRect(currPosX, currPosY + (gaugeHeight - quantHeight), currPosX + gaugeWidth, currPosY + gaugeHeight, color);
		GL11.glPushMatrix();
		{
			GL11.glTranslated(currPosX + gaugeWidth/2, currPosY-6, 0);
			double scale = 0.5;
			GL11.glScaled(scale, scale, scale);
			drawCenteredString(mc.fontRenderer, amount, 0, 0, 0xFFFFFF);
		}
		GL11.glPopMatrix();
		GL11.glPushMatrix();
		{
			GL11.glTranslated(currPosX + gaugeWidth/2, currPosY + gaugeHeight + 2, 0);
			double scale = 0.5;
			GL11.glScaled(scale, scale, scale);
			drawCenteredString(mc.fontRenderer, capacity, 0, 0, 0xFFFFFF);
		}
		GL11.glPopMatrix();
		currPosX += gaugeWidth + gaugeSpacer;
	}

	public void drawScalar(String string, float val, float min, float max) {
		//String minStr = String.format("%.1f", min);
		//String maxStr = String.format("%.1f", max);
		//drawRect(currPosX+ scalarWidth/2, currPosY, currPosX + scalarWidth/4, currPosY + scalarHeight, 0xFF4d4d4d);
		int quantHeight = scalarHeight - (int)((scalarHeight-5) * ((val-min) / (max-min))) - 5;
		drawRect(currPosX, currPosY + quantHeight, currPosX + scalarWidth, currPosY + quantHeight + 5, 0xFF999999);
		//drawCenteredString(mc.fontRenderer, minStr, currPosX + scalarWidth/2, currPosY + scalarHeight + 2, 0xFFFFFF);
		//drawCenteredString(mc.fontRenderer, maxStr, currPosX + scalarWidth/2, currPosY-12, 0xFFFFFF);
		GL11.glPushMatrix();
		{
			GL11.glTranslated(currPosX + scalarWidth/2, currPosY-6, 0);
			double scale = 0.5;
			GL11.glScaled(scale, scale, scale);
			drawCenteredString(mc.fontRenderer, string, 0, 0, 0xFFFFFF);
		}
		GL11.glPopMatrix();
		currPosX += scalarWidth + scalarSpacer;
	}
	
	public void drawSpeedDisplay(Locomotive loco, int offset) {
		double speed = Math.abs(loco.getCurrentSpeed().metric());
		String text = "";
		switch (ConfigGraphics.speedUnit) {
		case mph:
			text = String.format("%.2f mph", speed * 0.621371);
			break;
		case ms:
			text = String.format("%.2f m/s", speed / 3.6);
			break;
		case kmh:
		default:
			text = String.format("%.2f km/h", speed);
			break;
		}
		
		drawRect(currSpeedPosX + offset, currPosY - 10, currSpeedPosX + offset + 50, currPosY - 19, 0xFF4d4d4d);//drawRect(12, 265, 80, 248, 0xFF4d4d4d);
		GL11.glPushMatrix();
		{
			GL11.glTranslated(currSpeedPosX + 25 + offset, currPosY - 17, 0);
			double scale = 0.75;
			GL11.glScaled(scale, scale, scale);
			drawCenteredString(mc.fontRenderer, text, 0, 0, 0xFFFFFF);
		}
		GL11.glPopMatrix();
	}
	
	public void drawBackground(Locomotive loco) {
		if(loco instanceof LocomotiveSteam) {
			mc.renderEngine.bindTexture(OVERLAY_STEAM_TEXTURE);
			drawTexturedModalRect(bgPosX, bgPosY, 0, 0, 130, 85);
		}
		if(loco instanceof LocomotiveDiesel) {
			mc.renderEngine.bindTexture(OVERLAY_DIESEL_TEXTURE);
			drawTexturedModalRect(bgPosX, bgPosY, 0, 0, 110, 85);
		}
		if(loco instanceof HandCar) {
			mc.renderEngine.bindTexture(OVERLAY_HANDCAR_TEXTURE);
			drawTexturedModalRect(bgPosX, bgPosY, 0, 0, 60, 85);
		}
	}
	
	public void addSpace(int space) {
		currPosX += space;
	}
}