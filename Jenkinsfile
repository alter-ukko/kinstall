pipeline {
	agent any
	environment {
		VERSION = """${sh(
				returnStdout: true,
				script: 'sed -nE "s/^version=([^-]+)-SNAPSHOT/\1/p" gradle.properties'
			)}"""
	}
	stages {
		stage('version') {
			steps {
				echo "setting version to ${env.VERSION}"
				sh 'echo $VERSION'
			}		
		}
		stage('build') {
			steps {
				echo 'building...'
			}
		}
		stage('deploy') {
			steps {
				echo 'deploying...'
			}
		}
	}
}