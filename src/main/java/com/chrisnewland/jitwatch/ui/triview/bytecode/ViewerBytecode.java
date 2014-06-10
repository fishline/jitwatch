package com.chrisnewland.jitwatch.ui.triview.bytecode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import com.chrisnewland.jitwatch.model.IMetaMember;
import com.chrisnewland.jitwatch.model.Journal;
import com.chrisnewland.jitwatch.model.LineAnnotation;
import com.chrisnewland.jitwatch.model.bytecode.BytecodeInstruction;
import com.chrisnewland.jitwatch.model.bytecode.ClassBC;
import com.chrisnewland.jitwatch.model.bytecode.MemberBytecode;
import com.chrisnewland.jitwatch.model.bytecode.Opcode;
import com.chrisnewland.jitwatch.ui.IStageAccessProxy;
import com.chrisnewland.jitwatch.ui.triview.ILineListener;
import com.chrisnewland.jitwatch.ui.triview.Viewer;
import com.chrisnewland.jitwatch.ui.triview.ILineListener.LineType;
import com.chrisnewland.jitwatch.util.JVMSUtil;
import com.chrisnewland.jitwatch.util.JournalUtil;

public class ViewerBytecode extends Viewer
{
	private List<BytecodeInstruction> instructions = new ArrayList<>();

	public ViewerBytecode(IStageAccessProxy stageAccessProxy, ILineListener lineListener, LineType lineType)
	{
		super(stageAccessProxy, lineListener, lineType, true);
	}

	public void setContent(IMetaMember member, List<String> classLocations)
	{	
		ClassBC metaClassBytecode = member.getMetaClass().getClassBytecode(classLocations);

		if (metaClassBytecode != null)
		{
			MemberBytecode memberBytecode = metaClassBytecode.getMemberBytecode(member);

			if (memberBytecode != null)
			{
				instructions = memberBytecode.getInstructions();
			}
		}

		Map<Integer, LineAnnotation> annotations = null;

		lineAnnotations.clear();
		lastScrollIndex = -1;

		List<Label> labels = new ArrayList<>();

		if (instructions != null && instructions.size() > 0)
		{
			Journal journal = member.getJournal();

			annotations = JournalUtil.buildBytecodeAnnotations(journal, instructions);

			int maxOffset = instructions.get(instructions.size() - 1).getOffset();

			for (final BytecodeInstruction instruction : instructions)
			{
				BytecodeLabel lblLine = new BytecodeLabel(instruction, maxOffset);

				labels.add(lblLine);

				int offset = instruction.getOffset();

				String annotationText = null;

				if (annotations != null)
				{
					LineAnnotation annotation = annotations.get(offset);

					String unhighlightedStyle = STYLE_UNHIGHLIGHTED;
					
					if (annotation != null)
					{
						annotationText = annotation.getAnnotation();
						Color colour = annotation.getColour();

						unhighlightedStyle = STYLE_UNHIGHLIGHTED + "-fx-text-fill:" + toRGBCode(colour) + ";";

						lblLine.setTooltip(new Tooltip(annotationText));
					}
					
					lblLine.setUnhighlightedStyle(unhighlightedStyle);
				}

				lblLine.setOnMouseClicked(new EventHandler<MouseEvent>()
				{
					@Override
					public void handle(MouseEvent mouseEvent)
					{
						if (mouseEvent.getButton().equals(MouseButton.PRIMARY))
						{
							if (mouseEvent.getClickCount() == 2)
							{
								Opcode opcode = instruction.getOpcode();

								browseMnemonic(opcode);
							}
						}
					}
				});
			}
		}

		setContent(labels);
	}

	public int getLineIndexForBytecodeOffset(int offset)
	{
		int result = -1;

		int pos = 0;

		for (BytecodeInstruction instruction : instructions)
		{
			if (instruction.getOffset() == offset)
			{
				result = pos;
				break;
			}

			pos++;
		}

		return result;
	}

	private String toRGBCode(Color color)
	{
		return String.format("#%02X%02X%02X", (int) (color.getRed() * 255), (int) (color.getGreen() * 255),
				(int) (color.getBlue() * 255));
	}

	private void browseMnemonic(final Opcode opcode)
	{
		if (JVMSUtil.hasLocalJVMS())
		{
			if (!JVMSUtil.isJVMSLoaded())
			{
				JVMSUtil.loadJVMS();
			}

			String html = JVMSUtil.getBytecodeDescriptions(opcode);

			String cssURI = JVMSUtil.getJVMSCSSURL();

			stageAccessProxy.openBrowser("JMVS Browser - " + opcode.getMnemonic(), html, cssURI);
		}
		else
		{
			stageAccessProxy.openBrowser("Fetching JVM Spec", "Downloading a local copy of the JVM Specification", null);

			new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					boolean success = JVMSUtil.fetchJVMS();

					if (success)
					{
						JVMSUtil.loadJVMS();

						final String html = JVMSUtil.getBytecodeDescriptions(opcode);

						final String cssURI = JVMSUtil.getJVMSCSSURL();

						Platform.runLater(new Runnable()
						{
							@Override
							public void run()
							{
								stageAccessProxy.openBrowser("JMVS Browser - " + opcode.getMnemonic(), html, cssURI);
							}
						});
					}
					else
					{
						Platform.runLater(new Runnable()
						{
							@Override
							public void run()
							{
								stageAccessProxy.openBrowser("Downloading Failed",
										"Unable to download a local copy of the JVM Specification", null);
							}
						});
					}
				}
			}).start();
		}
	}
}
