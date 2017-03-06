
import pandas as pd
import re
import csv

def clean(data):
    data = re.sub(r'[\s\n\r\t"():/,]', ' ', data)
    data = re.sub(r'<pre.*pre>', '', data)
    data = re.sub(r'<.*?>', '', data)
    data = re.sub(r'\s+', ' ', data)
    return data.strip()
    
def prepare(df):
    questions = []
    u_count = {}
    for index, row in df.iterrows():
        q = clean(row["QuestionTitle"] + " " + row["QuestionBody"])
        users = row['AnswerUsers'].split(",")
        questions.append({
                'q': q[:1000],
                'a_list': users
            })
        for u in users:
            if u_count.get(u):
                u_count[u]+=1
            else:
                u_count[u]=1
    return (questions, u_count)

def write_csv(questions, u_count, cutoff, filename):
    count=0
    with open(filename, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        for question in questions:
            users=[]
            for a in question['a_list']:
                if(u_count[a]>cutoff):
                    users.append(a)
            if len(users)>0:
                writer.writerow([question['q']] + users)
                count+=1
        print("Exported ", count, "questions!")

df = pd.read_csv("sodata.csv")
(questions, u_count) = prepare(df)
write_csv(questions, u_count, 10, 'output.csv')

