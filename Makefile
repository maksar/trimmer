REPO   := docker-registry.itransition.corp
NAME   := trimmer
TAG    := $$(git log -1 --pretty=%H)
IMG    := ${NAME}:${TAG}
LATEST := ${NAME}:latest

all: build push

build:
	@docker build -t ${NAME} .
	@docker tag ${NAME} ${IMG}
	@docker tag ${IMG} ${REPO}/${IMG}
	@docker tag ${IMG} ${REPO}/${LATEST}

push:
	@docker push ${REPO}/${NAME}