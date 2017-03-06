import json
from watson_developer_cloud import NaturalLanguageClassifierV1

cred_user = '<Your username>'
cred_pass = '<Your password>'
classifier_name = 'SOClassifier'

def classify(classifier_name, filename):
    natural_language_classifier = NaturalLanguageClassifierV1(
      username=cred_user,
      password=cred_pass)

    with open(filename, 'rb') as training_data:
      classifier = natural_language_classifier.create(
        training_data=training_data,
        name=classifier_name,
        language='en'
      )
    print(json.dumps(classifier, indent=2))

def check_status(classifier_id):
    natural_language_classifier = NaturalLanguageClassifierV1(
      username=cred_user,
      password=cred_pass)
    
    status = natural_language_classifier.status(classifier_id)
    print (json.dumps(status, indent=2))

# Comment the classify call and you can use the check_status command
classify(classifier_name, "output.csv")
#check_status("<Your Classifier ID>")
