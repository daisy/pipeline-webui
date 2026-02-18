.PHONY : help
help :
	@echo "make help:"                              >&2 && \
	 echo "\tPrint list of commands"                >&2 && \
	 echo "make docker-image:"                      >&2 && \
	 echo "\tBuild, tag and push the Docker image"  >&2

.PHONY : docker-image
docker-image :
	docker build -t "daisyorg/pipeline-webui:latest-snapshot" .
	docker image push "daisyorg/pipeline-webui:latest-snapshot"
