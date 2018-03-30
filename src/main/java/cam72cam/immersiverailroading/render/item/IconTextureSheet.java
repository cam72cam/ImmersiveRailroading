package cam72cam.immersiverailroading.render.item;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import cam72cam.immersiverailroading.registry.DefinitionManager;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.render.StockRenderCache;
import cam72cam.immersiverailroading.render.entity.StockModel;
import cam72cam.immersiverailroading.util.GLBoolTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;

public class IconTextureSheet {
	public class UV {
		public final float u;
		public final float v;
		
		public UV(int uPx, int vPx) {
			this.u = (float)uPx / maxSheetSize;
			this.v = (float)vPx / maxSheetSize;
		}
	}

	private Map<String, UV> icons = new HashMap<String, UV>();
	public final int textureID;
	private int uPx;
	private int vPx;
	private boolean debug = true;
	
	private static final int maxSheetSize = 2048;//sane default
	private static final int texSize = 64;
	private static final int bpp = 4;
	
	public IconTextureSheet() {
		uPx = 0;
		vPx = 0;
		textureID = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
		TextureUtil.allocateTexture(textureID, maxSheetSize, maxSheetSize);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
	}

	public void add(String defID, Runnable render) {
		UV uv = new UV(uPx, vPx);
		
		icons.put(defID, uv);
		
		EntityRollingStockDefinition def = DefinitionManager.getDefinition(defID);
		ByteBuffer buffer = BufferUtils.createByteBuffer(texSize * texSize * bpp);
		String[][] map = def.getIcon(texSize);
		
		StockModel renderer = StockRenderCache.getRender(defID);
		
		for (int x = 0; x < texSize; x++) {
			for (int y = 0; y < texSize; y++) {
				/*
				int color = map[x][y];
				int red = color >> 16 & 255;
				int green = color >> 8 & 255;
	            int blue = color & 255;
	            int alpha = color >> 24 & 255;
	            alpha = 0xFF;
	            */
				int i = (x + (texSize * y)) * bpp;
				
				if (map[x][y] != null && map[x][y] != "") {
					int color = renderer.texture.samp(map[x][y]);
					
					int red = color >> 16 & 255;
					int green = color >> 8 & 255;
		            int blue = color & 255;
		            //int alpha = color >> 24 & 255;
					
					buffer.put(i, (byte)red);
					buffer.put(i+1, (byte)green);
					buffer.put(i+2, (byte)blue);
					//buffer.put(i+2, (byte)blue);
				} else {
					buffer.put(i, (byte) 0);
					buffer.put(i+1, (byte) 0);
					buffer.put(i+2, (byte) 0);
					//buffer.put(i+3, (byte) 0);
				}
			}
		}
		
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, uPx, vPx, texSize, texSize, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
		
		if (debug) {
			buffer.position(0);
			debugIcon(buffer);
		}
		
		uPx += texSize;
		if (uPx >= maxSheetSize + texSize) {
			uPx = 0;
			vPx += texSize;
		}
	}

	private int debugID = 0;
	private int prevTexture;
	private void debugIcon(ByteBuffer buffer) {
		debugID ++;
		File file = new File("/home/gilligan/foo" + debugID + ".png"); // The
		String format = "PNG"; // Example: "PNG" or "JPG"
		BufferedImage image = new BufferedImage(texSize, texSize, BufferedImage.TYPE_INT_RGB);

		for (int x = 0; x < texSize; x++) {
			for (int y = 0; y < texSize; y++) {
				int i = (x + (texSize * y)) * bpp;
				int r = buffer.get(i) & 0xFF;
				int g = buffer.get(i + 1) & 0xFF;
				int b = buffer.get(i + 2) & 0xFF;
				image.setRGB(x, texSize - (y + 1), (0xFF << 24) | (r << 16) | (g << 8) | b);
			}
		}

		try {
			ImageIO.write(image, format, file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void bindTexture() {
		int currentTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		if (currentTexture != this.textureID) {
			prevTexture  = currentTexture;
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureID);
		}
	}
	
	private void restoreTexture() {
		if (prevTexture != -1) {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
			prevTexture = -1;
		}
	}

	public boolean renderIcon(String defID) {
		if (!icons.containsKey(defID)) {
			return false;
		}
		
		UV uv = icons.get(defID);
		float delta = ((float)texSize) / maxSheetSize;
		
		bindTexture();

		GL11.glPushMatrix();
		{
			GLBoolTracker tex = new GLBoolTracker(GL11.GL_TEXTURE_2D, true);
			
			bindTexture();

			GL11.glTranslated(0, 0, 0.5);
			GL11.glRotated(-90, 0, 1, 0);
			
			GL11.glColor4f(1, 1, 1, 1);
			
			int size = 1;
			GL11.glBegin(GL11.GL_QUADS);
			GL11.glTexCoord2d(uv.u, uv.v + delta);
			GL11.glVertex3d(0, 0, 0);
			GL11.glTexCoord2d(uv.u, uv.v);
			GL11.glVertex3d(0, size, 0);
			GL11.glTexCoord2d(uv.u + delta, uv.v);
			GL11.glVertex3d(-size, size, 0);
			GL11.glTexCoord2d(uv.u + delta, uv.v + delta);
			GL11.glVertex3d(-size, 0, 0);
			GL11.glEnd();
			
			restoreTexture();
			
			tex.restore();
		}
		GL11.glPopMatrix();
		
		restoreTexture();
		
		return true;
	}
}
