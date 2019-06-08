package podcast;

import org.springframework.context.ApplicationEvent;

import java.io.File;

abstract class FileEvent extends ApplicationEvent {

	@Override
	public File getSource() {
		return (File) super.getSource();
	}

	public FileEvent(File source) {
		super(source);
	}

}
