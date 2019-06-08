package podcast;

import org.springframework.context.ApplicationEvent;

class PodcastStartedEvent extends ApplicationEvent {

	PodcastStartedEvent(Podcast source) {
		super(source);
	}

	@Override
	public Podcast getSource() {
		return (Podcast) super.getSource();
	}

}
