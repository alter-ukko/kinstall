pipeline {
	agent any
	environment {
		VERSION = """${sh(
				returnStdout: true,
				script: 'sed -nE "s/^version=([^-]+)-SNAPSHOT/\\1/p" gradle.properties'
			).trim()}.${env.BUILD_ID}"""
	}
	stages {
		stage('version') {
			steps {
				echo "setting version to ${env.VERSION}"
				sh 'git checkout main'
				sh 'sed -i "s/^version=.*/version=${VERSION}/" gradle.properties'
			}		
		}
		stage('build') {
			steps {
				echo 'building...'
				sh './gradlew build'
			}
		}
		stage('release') {
			environment {
				GITHUB_KEY = credentials('alter-ukko-github-ssh')
			}
			steps {
				echo "relasing verion ${env.VERSION}..."
				sh 'git config --global user.email "alter.ukko@gmail.com"'
				sh 'git config --global user.name "alter-ukko"'
				sh 'git add gradle.properties'
				sh 'git commit -m "update version to ${VERSION}"'
				sh 'git push'
				sh 'git tag $VERSION'
				sh 'git push origin $VERSION'
			}
		}
		stage('deploy') {
			steps {
				echo 'deploying...'
			}
		}
	}
}