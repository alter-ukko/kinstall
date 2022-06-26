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
				sh 'sed -i "s/^version=.*/version=${VERSION}/" gradle.properties'
			}		
		}
		stage('build') {
			steps {
				echo 'building...'
				sh 'gradle build'
			}
		}
		stage('release') {
				echo "relasing verion ${env.VERSION}..."
				sh 'git add gradle.properties'
				sh 'git commit -m "update version to ${VERSION}"'
				sh 'git push'
				sh 'git tag $VERSION'
				sh 'git push origin $VERSION'			
		}
		stage('deploy') {
			steps {
				echo 'deploying...'
			}
		}
	}
}