package podcast;


import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.shell.Availability;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@SpringBootApplication
public class PodcastShell {

	public static void main(String args[]) {
		SpringApplication.run(PodcastShell.class, args);
	}
}


@Component
class PodcastPromptProvider implements PromptProvider {

	private Podcast podcast;
	private File introduction;
	private File interview;

	@Override
	public AttributedString getPrompt() {

		String promptTerminal = " :>";

		if (podcast != null) {
			return new AttributedString(this.buildPromptForPodcast() + promptTerminal,
				AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
		}
		else {
			return new AttributedString("(no podcast created yet) " + promptTerminal,
				AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
		}
	}

	@EventListener
	public void handle(IntroductionFileEvent event) {
		this.introduction = event.getSource();
	}

	@EventListener
	public void handle(InterviewFileEvent event) {
		this.interview = event.getSource();
	}

	@EventListener
	public void handle(PodcastStartedEvent event) {
		this.podcast = event.getSource();
	}

	private String buildPromptForPodcast() {
		String introductionLabel = "introduction: ", interviewLabel = "interview: ";
		String msg = "";
		if (podcast != null) {
			msg = podcast.getDescription() + " ";
			var list = new ArrayList<String>();

			addLabeledFileToPrompt(introductionLabel, list, this.introduction);
			addLabeledFileToPrompt(interviewLabel, list, this.interview);

			if (list.size() > 0) {
				var strings = list.toArray(new String[0]);
				var filesString = StringUtils.arrayToDelimitedString(strings, ", ");
				msg += "( " + filesString + " )";
			}
		}
		return msg;
	}

	private static void addLabeledFileToPrompt(String introductionLabel, ArrayList<String> list, File introduction) {
		if (null != introduction) {
			list.add(introductionLabel + introduction.getName());
		}
	}
}

class PodcastStartedEvent extends ApplicationEvent {

	PodcastStartedEvent(Podcast source) {
		super(source);
	}

	@Override
	public Podcast getSource() {
		return (Podcast) super.getSource();
	}
}

abstract class FileEvent extends ApplicationEvent {

	@Override
	public File getSource() {
		return (File) super.getSource();
	}

	public FileEvent(File source) {
		super(source);
	}
}

class IntroductionFileEvent extends FileEvent {

	IntroductionFileEvent(File source) {
		super(source);
	}
}


class PackageCreatedEvent extends FileEvent {

	public PackageCreatedEvent(File source) {
		super(source);
	}
}

class InterviewFileEvent extends FileEvent {

	InterviewFileEvent(File source) {
		super(source);
	}
}

@Log4j2
@ShellComponent
@RequiredArgsConstructor
class PodcastCommands {

	private static final String MEDIA_ARG = "--media";
	private static final String DISCOVERY_ARG = "--description";
	private final ApplicationEventPublisher publisher;
	private final ThreadLocal<Podcast> podcast = new ThreadLocal<>();

	private File intro, interview, archive;


	public Availability newPodcastAvailabilityCheck() {
		return !isPodcastStarted() ? Availability.available() : Availability.unavailable("you're already producing a new podcast");
	}

	private static void log(String msg) {
		log.info(msg);
	}

	public Availability addMediaAvailabilityCheck() {
		return isPodcastStarted() ? Availability.available() : Availability.unavailable("you need to start a new podcast");
	}

	@ShellMethodAvailability("newPodcastAvailabilityCheck")
	@ShellMethod(value = "new podcast")
	public void newPodcast(@ShellOption(DISCOVERY_ARG) String description) {
		this.podcast.set(new Podcast(description, UUID.randomUUID().toString()));
		this.publisher.publishEvent(new PodcastStartedEvent(getPodcast()));
	}

	@ShellMethodAvailability(value = "addMediaAvailabilityCheck")
	@ShellMethod(value = "add introduction media")
	public void addIntroductionMedia(@ShellOption(MEDIA_ARG) File file) {
		Assert.isTrue(isValidArtifact(file), "you must provide a valid artifact");
		this.intro = file;
	}

	@ShellMethodAvailability(value = "addMediaAvailabilityCheck")
	@ShellMethod(value = "add interview media")
	public void addInterviewMedia(@ShellOption(MEDIA_ARG) File file) {
		Assert.isTrue(isValidArtifact(file), "you must provide a valid artifact");
		this.interview = file;
	}

	private boolean isValidArtifact(File f) {
		return (f != null && Arrays.asList("wav", "mp3").contains(extensionFor(f)));
	}

	private String extensionFor(File file) {
		var name = file.getName();
		var lastIndexOf = name.lastIndexOf(".");
		var trim = name.substring(lastIndexOf).toLowerCase().trim();
		if (trim.startsWith(".")) {
			return trim.substring(1);
		}
		return trim;
	}

	@ShellMethod
	public void publishForProcessing() {
		//todo this is where we would publish the pacakge to the integration endpoint
	}

	@ShellMethod(value = "package")
	public void createPackage() {
		var ext = extensionFor(this.intro);
		var aPackage = this.getPodcast()
			.addMedia(ext, new Media(ext, this.intro, this.interview))
			.createPackage();

		publisher.publishEvent(new PackageCreatedEvent(aPackage));
	}

	@EventListener
	public void packageCreated(PackageCreatedEvent event) {
		this.archive = event.getSource();
		System.out.println("The podcast archive has been written to " + event.getSource().getAbsolutePath());
	}

	private Podcast getPodcast() {
		return this.podcast.get();
	}

	private boolean isPodcastStarted() {
		return (getPodcast() != null);
	}
}

@Data
class Media {

	private final String format;

	private final File intro, interview;

	Media(String format, File intro, File interview) {
		Assert.notNull(format, "the format must not be null");
		Assert.notNull(interview, "the interview file must not be null");
		Assert.notNull(intro, "the intro file must not be null");
		this.format = format;
		this.intro = intro;
		this.interview = interview;
	}
}

@Log4j2
class Podcast {

	private String description, uid;
	private String MP3_EXT = "mp3";
	private String WAV_EXT = "wav";

	private final Map<String, Optional<Media>> media = new ConcurrentHashMap<>();

	Podcast(String description, String uuid) {
		this.description = description;
		this.uid = uuid;

		this.media.put(MP3_EXT, Optional.empty());
		this.media.put(WAV_EXT, Optional.empty());
	}

	public String getDescription() {
		return description;
	}


	public Podcast addMedia(String ext, Media media) {
		this.media.put(ext, Optional.of(media));
		return this;
	}

	public File createPackage() {
		return doCreatePackage(this.description,
			this.uid,
			this.media.get(MP3_EXT).orElse(null),
			this.media.get(WAV_EXT).orElse(null)
		);
	}

	@SneakyThrows
	private static File doCreatePackage(String description, String uid, Media mp3, Media wav) {

		var staging = Files.createTempDirectory("staging").toFile();

		var xmlFile = new File(staging, "manifest.xml");
		try (var xmlOutputStream = new BufferedWriter(new FileWriter(xmlFile))) {
			var xml = buildXmlManifestForPackage(description, uid, mp3, wav);
			FileCopyUtils.copy(xml, xmlOutputStream);
			log.debug("wrote " + xmlFile.getAbsolutePath() + " with content " + xml);
		}

		var zipFile = new File(staging, UUID.randomUUID().toString() + ".zip");
		var srcFiles = new ArrayList<File>();
		srcFiles.add(xmlFile);
		addMediaFilesToPackage(mp3, srcFiles);
		addMediaFilesToPackage(wav, srcFiles);

		try (var outputStream = new BufferedOutputStream(new FileOutputStream(zipFile));
							var zipOutputStream = new ZipOutputStream(outputStream)) {
			for (var fileToZip : srcFiles) {
				try (var inputStream = new BufferedInputStream(
					new FileInputStream(fileToZip))) {
					var zipEntry = new ZipEntry(fileToZip.getName());
					zipOutputStream.putNextEntry(zipEntry);
					StreamUtils.copy(inputStream, zipOutputStream);
				}
			}
		}
		return zipFile;
	}

	private static void addElementFor(Document doc, Element root, String elementName, Map<String, String> attrs) {
		Element element = doc.createElement(elementName);
		attrs.forEach(element::setAttribute);
		root.appendChild(element);
	}

	private static void addAttributesForMedia(Document doc, Element root, Media media) {
		if (null == media) {
			return;
		}
		var intro = media.getIntro();
		var interview = media.getInterview();
		var attrs = Map.of("intro", intro.getName(), "interview", interview.getName());
		addElementFor(doc, root, media.getFormat(), attrs);
	}

	@SneakyThrows
	private static String buildXmlManifestForPackage(String description, String uid, Media mp3, Media wav) {

		var docFactory = DocumentBuilderFactory.newInstance();
		var docBuilder = docFactory.newDocumentBuilder();

		var doc = docBuilder.newDocument();
		var rootElement = doc.createElement("podcast");
		rootElement.setAttribute("description", description);
		rootElement.setAttribute("uid", uid);
		doc.appendChild(rootElement);

		addAttributesForMedia(doc, rootElement, mp3);
		addAttributesForMedia(doc, rootElement, wav);

		var transformerFactory = TransformerFactory.newInstance();

		var transformer = transformerFactory.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		// transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

		var source = new DOMSource(doc);
		var stringWriter = new StringWriter();
		var result = new StreamResult(stringWriter);
		transformer.transform(source, result);
		return stringWriter.toString();
	}

	private static void addMediaFilesToPackage(Media m, Collection<File> files) {
		if (null == m)
			return;
		files.add(m.getInterview());
		files.add(m.getIntro());
	}

}
