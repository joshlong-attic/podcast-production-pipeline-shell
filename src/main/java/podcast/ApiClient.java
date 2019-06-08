package podcast;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;

@Component
class ApiClient {

	private final String serverUrl;
	private final RestTemplate restTemplate;

	ApiClient(@Value("${podcast.api.url}") String serverUrl,
											RestTemplate template) {
		this.serverUrl = serverUrl;
		this.restTemplate = template;
	}

	public boolean publish(File archivePackage) {
		var headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		var resource = new FileSystemResource(archivePackage);
		var body = new LinkedMultiValueMap<String, Object>();
		body.add("file", resource);
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		var response = restTemplate.postForEntity(serverUrl, requestEntity, String.class);
		return response.getStatusCode().is2xxSuccessful();
	}
}
