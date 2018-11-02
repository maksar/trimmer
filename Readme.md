# JIRA Trimmer

Trims (removes spaces around) subjects of all Resource Cards and Project Cards.

## Configuration

Edit configuration file by adding jira username and password to use.

    $ cp .env.example .env
    $ vim .env

## Running

Run [gradle](https://gradle.org/install/) `run` task.

    $ gradle run

Or you can use Docker

    $ docker build -t trimmer . && docker run -it --rm -v $PWD/.env:/app/.env trimmer

## Example output

    Searching for issues in RESCARD.
    Found 1 issues in RESCARD, that needs to be trimmed.
    Trimming RESCARD-9129 with summary 'MobileTime - Groups '.
    Searching for issues in PROJCARD.
    No issues in PROJCARD, that needs to be trimmed was found.