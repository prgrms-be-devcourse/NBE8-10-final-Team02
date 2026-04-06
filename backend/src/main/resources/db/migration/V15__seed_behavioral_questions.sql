-- 인성 면접 질문 시드 데이터 (behavioral-questions.json에서 생성)

INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_001 [standard]', '자기소개를 해보세요.

[AI Guide]
지원자의 기술 스택과 프로젝트 요약을 바탕으로 핵심 강점 1~2가지를 드러내는 자기소개를 유도한다.', 'data/behavioral-questions.json', '4d3b64a3a5785ebddc9c2870dcc59321be53bd9c9cad5b08d68259fe02a004b2', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_001 [pressure]', '본인이 잘 드러나도록, 본인만의 차별점을 말해보세요.

[AI Guide]
지원자의 기술 스택과 프로젝트 요약을 바탕으로 핵심 강점 1~2가지를 드러내는 자기소개를 유도한다.', 'data/behavioral-questions.json', '0d7727f07cd00aee44bcd941c3df0fbc8b8f29e169cc34690f5e85f023df44aa', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_001 [situational]', '만약 지금 당장 저희 팀에 합류하신다면, 첫 한 달 동안 본인의 어떤 강점을 발휘해서 팀에 기여하실 수 있나요?

[AI Guide]
지원자의 기술 스택과 프로젝트 요약을 바탕으로 핵심 강점 1~2가지를 드러내는 자기소개를 유도한다.', 'data/behavioral-questions.json', '9b1b213c55355af1f14d1724cecf91a78e2915d77bc82094aa930c00eba3ff94', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_002 [standard]', '왜 개발자가 되고 싶으신가요?

[AI Guide]
지원자가 개발을 시작한 계기와 지속 동기를 연결해 진정성 있는 답변을 이끌어낸다.', 'data/behavioral-questions.json', 'dccf42878ae659b984b73d65fe146942bbd054ce080fbd387ef6460119c5db6d', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_002 [pressure]', '이 직무를 선택한 이유가 있나요?

[AI Guide]
지원자가 개발을 시작한 계기와 지속 동기를 연결해 진정성 있는 답변을 이끌어낸다.', 'data/behavioral-questions.json', 'a66ecbda16e50e939bea2f5134adc7ca81e1b1e64c061f4a5a2260fbccd5c168', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_002 [situational]', '만약 비개발 직무를 제안받는다면 어떻게 하시겠어요?

[AI Guide]
지원자가 개발을 시작한 계기와 지속 동기를 연결해 진정성 있는 답변을 이끌어낸다.', 'data/behavioral-questions.json', 'fecbf0a4f6ee992b57130c96c3c5f57764408b2bd0508b09d46f868d89e2620d', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_003 [standard]', '우리 회사에 지원한 이유가 무엇인가요?

[AI Guide]
회사의 도메인, 기술 스택, 제품 방향성과 지원자의 관심사를 연결하도록 유도한다.', 'data/behavioral-questions.json', '8c9bbe8980c95e68b9630e67edafc277c08fa6227cf1767367601933005c5c48', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_003 [pressure]', '우리 회사와 경쟁사의 차이를 구체적으로 알고 지원하셨나요?

[AI Guide]
회사의 도메인, 기술 스택, 제품 방향성과 지원자의 관심사를 연결하도록 유도한다.', 'data/behavioral-questions.json', '0aa17f8f1e3162df3bcf9a5d6fa03bd656ace7ee447016fa165efaeda379ecba', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_004 [standard]', '입사 후 첫 1년 동안 어떤 것을 배우고 싶으신가요?

[AI Guide]
단기 학습 목표와 팀 기여 의지를 함께 드러내는 방향으로 답변을 구성하도록 안내한다.', 'data/behavioral-questions.json', '1d24e520215ceac64f3cfbdfcc778d2f67f4a07caf16a240af19070da75f9526', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_004 [situational]', '온보딩 혹은 인턴 기간 중 기대했던 것과 실제가 달랐다면 어떻게 대처하시겠어요?

[AI Guide]
단기 학습 목표와 팀 기여 의지를 함께 드러내는 방향으로 답변을 구성하도록 안내한다.', 'data/behavioral-questions.json', '04e7937954f8bd72bab6c1c55bc9e47b4df8a77d10bb7be5867728a81810a483', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_005 [standard]', '10년 후 자신의 모습을 말해보세요.

[AI Guide]
현실적인 커리어 비전과 지속 성장 의지를 확인한다.', 'data/behavioral-questions.json', '5e87e46f8a4f4940c7ab7f80746e77e3d56989fe85e6666d4b2c061216979f46', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_005 [pressure]', '개발자로서 10년 후에도 코딩을 하고 있을 것 같으신가요?

[AI Guide]
현실적인 커리어 비전과 지속 성장 의지를 확인한다.', 'data/behavioral-questions.json', '9597459062ceefead8c5299f6cd821bde45d389464d911ad9469dce64cd7bc4b', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_006 [standard]', '커리어 목표가 무엇인가요?

[AI Guide]
단순 직함이 아니라 어떤 문제를 해결하는 개발자가 되고 싶은지에 초점을 맞춘다.', 'data/behavioral-questions.json', 'ab40da8b1f09bb49c422da6a1f3540afb2d8ec9b1180f0e8d603aa50dd384fcc', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_007 [standard]', '자신의 강점과 약점을 말해보세요.

[AI Guide]
강점은 구체적인 사례로, 약점은 개선 노력과 함께 제시하도록 안내한다.', 'data/behavioral-questions.json', 'b633c4428e98b786e3f2da4893b2b1b38adeae8f3d229cf6d4cbdcaf2eff05ee', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_007 [pressure]', '약점을 극복하려고 시도했지만 실패한 경험이 있나요?

[AI Guide]
강점은 구체적인 사례로, 약점은 개선 노력과 함께 제시하도록 안내한다.', 'data/behavioral-questions.json', '92f75bcfbde507e6e896bf6b1a48271305f3c0fab1d0793ce48f27313d422657', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_007 [situational]', '팀원이 당신의 약점을 지적한다면 어떻게 반응하시겠어요?

[AI Guide]
강점은 구체적인 사례로, 약점은 개선 노력과 함께 제시하도록 안내한다.', 'data/behavioral-questions.json', '8c2d23186b5b0419196b7ad2a620ec76701b4d085d03058da790feca6f556970', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_008 [standard]', '본인이 가장 자랑스럽게 생각하는 경험은 무엇인가요?

[AI Guide]
STAR 구조(상황-과제-행동-결과)로 서술하도록 유도한다.', 'data/behavioral-questions.json', 'de19ffc49435b146bf7a495d796711c8bb9c6ce3c4c88279e3e2640c8e41a59d', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_008 [pressure]', '그 경험에서 본인의 역할이 없었다면 결과가 달라졌을까요?

[AI Guide]
STAR 구조(상황-과제-행동-결과)로 서술하도록 유도한다.', 'data/behavioral-questions.json', '81a549ad53fc615bbdcd4294b7ef3599f1a7634b9f0166bc5fb6dc5be7248700', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_009 [standard]', '스트레스를 어떻게 해소하나요?

[AI Guide]
스트레스 관리 방식이 업무 지속성과 팀 분위기에 미치는 영향을 확인한다.', 'data/behavioral-questions.json', 'f06df8bc7344a31a5dbbd3d72fdfb731942dd393a326e2ece5661bb3bc9b9817', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_009 [situational]', '마감 직전에 치명적인 버그를 발견했다면 어떻게 대처하시겠어요?

[AI Guide]
스트레스 관리 방식이 업무 지속성과 팀 분위기에 미치는 영향을 확인한다.', 'data/behavioral-questions.json', 'c3f185d8f24e2975d753b432c9d5bcd924e2f7255139d253dbe951cd81614dff', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_010 [standard]', '실패 경험과 그로부터 무엇을 배웠는지 말해보세요.

[AI Guide]
실패를 숨기지 않고 학습과 성장의 맥락에서 서술하도록 유도한다.', 'data/behavioral-questions.json', 'de912e0a2a4f60d762a4eada6a12e22ff79c8a23c31550cf6d9d21caab53702f', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_010 [pressure]', '그 실패가 팀 전체에 영향을 미쳤다면 어떻게 책임졌을 것 같나요?

[AI Guide]
실패를 숨기지 않고 학습과 성장의 맥락에서 서술하도록 유도한다.', 'data/behavioral-questions.json', 'e886ae1030cf238b674d086b7d6451c327f04d556ba8e95664b06a3ab91c7ea3', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_011 [standard]', '팀 프로젝트에서 갈등이 생겼을 때 어떻게 해결했나요?

[AI Guide]
갈등 해결 과정에서 커뮤니케이션 방식과 팀 우선 태도를 드러내도록 안내한다.', 'data/behavioral-questions.json', '25a2f47370aac81d78b92e8d4b0409453af70de13979206cb6b8d59225ef7ddb', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_011 [pressure]', '의견 충돌 상황에서 본인이 옳다고 확신했지만 팀 결정을 따른 경험이 있나요?

[AI Guide]
갈등 해결 과정에서 커뮤니케이션 방식과 팀 우선 태도를 드러내도록 안내한다.', 'data/behavioral-questions.json', '753d40753be8f168ca46a9318eb7d1e11a80e20e56d5d97a1740dfdb3d14ab89', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_011 [situational]', '코드 리뷰에서 동료의 의견을 받아들이기 어려웠던 경험이 있다면 어떻게 했나요?

[AI Guide]
갈등 해결 과정에서 커뮤니케이션 방식과 팀 우선 태도를 드러내도록 안내한다.', 'data/behavioral-questions.json', 'caa5151987b34882dea596462e2edb4cf75655f6d33d4e393d29ecf74abda491', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_012 [standard]', '팀원과 의견 충돌이 있을 때 어떻게 조율하나요?

[AI Guide]
논리적 설득과 수용의 균형을 보여주도록 유도한다.', 'data/behavioral-questions.json', '557b7a84e234fb4eba4127344cd0b3289e023142d83c4494f1d19f7500f74c88', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_012 [situational]', '기술적 판단에서 시니어와 의견이 다를 경우 어떻게 하시겠어요?

[AI Guide]
논리적 설득과 수용의 균형을 보여주도록 유도한다.', 'data/behavioral-questions.json', '6cfd3b9eee1ee2b603a243f0762f94f770aa9b4434d934c1ee3d23803d3a741c', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_013 [standard]', '리더십을 발휘한 경험이 있나요?

[AI Guide]
기술 리더십뿐 아니라 팀 문화 기여 측면도 함께 드러내도록 한다.', 'data/behavioral-questions.json', '61325cb3c08b6850636983f8027349d90a84d2590f674b0ab0b9e40f1c76254a', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_013 [pressure]', '팀원이 역할을 제대로 수행하지 못할 때 어떻게 대응하시겠어요?

[AI Guide]
기술 리더십뿐 아니라 팀 문화 기여 측면도 함께 드러내도록 한다.', 'data/behavioral-questions.json', '26223ed7cbd13c4cb2862f2c48c7bbc10c2ac04859a67590e3ba5b6b23136532', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_014 [standard]', '동료에게 어떤 사람으로 기억되고 싶으신가요?

[AI Guide]
추상적 답변이 아닌 구체적 행동 기반으로 서술하도록 유도한다.', 'data/behavioral-questions.json', 'a35fcc9397fbf2487b9dc1a2e80e58198730b93ef939ebe975faa78d8e679e80', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_015 [standard]', '최근에 공부하고 있는 기술이 있나요?

[AI Guide]
학습 동기와 실무 연결 능력을 함께 확인한다.', 'data/behavioral-questions.json', '8e59f705ed1ccb06c27e2e0a38bd5347c1f0986a1064038a876fcf281c6a19ce', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_015 [pressure]', '공부한 기술을 실제 프로젝트에 적용해본 적이 있나요? 결과는 어땠나요?

[AI Guide]
학습 동기와 실무 연결 능력을 함께 확인한다.', 'data/behavioral-questions.json', '3443daf58ce3fb2d454383cd6d30ffa58c0e53adefb71a497850cf144856dcf4', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_016 [standard]', '개발 트렌드를 어떻게 파악하나요?

[AI Guide]
정보 습득 채널과 기술 선별 기준을 구체적으로 표현하도록 안내한다.', 'data/behavioral-questions.json', 'fc3b2014941ce0b2a974fd04dca1ae1ed34b3b25541e20d560d41bef0844b852', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_016 [situational]', '팀에서 새로운 기술 도입을 제안해야 한다면 어떤 기준으로 판단하겠어요?

[AI Guide]
정보 습득 채널과 기술 선별 기준을 구체적으로 표현하도록 안내한다.', 'data/behavioral-questions.json', 'e9527fe6d9199e8f15ec742248436d9745aea791fa17205a056566ac53524f2b', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_017 [standard]', '코드 리뷰를 받았을 때 어떻게 반응하나요?

[AI Guide]
피드백 수용 태도와 기술적 소통 방식을 동시에 확인한다.', 'data/behavioral-questions.json', 'a94e2a11d59dd71ee83a1fbc29d422a7f945a60664a90e89e7d16a1ec87f37c0', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_017 [pressure]', '리뷰어의 의견이 명백히 틀렸다고 생각될 때 어떻게 하나요?

[AI Guide]
피드백 수용 태도와 기술적 소통 방식을 동시에 확인한다.', 'data/behavioral-questions.json', '3c04f414ba2e86a0ee673516538ed7836483c9e35c6b4eb0be40e189b8ceb9b4', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_018 [standard]', '본인의 코드에 문제가 발견되었을 때 어떻게 대처하나요?

[AI Guide]
문제 인식 → 공유 → 해결 → 재발 방지의 흐름으로 서술하도록 유도한다.', 'data/behavioral-questions.json', 'b4451fc37041a264a64505c79c00b47cc08748fa866327b4505120e9c1d30113', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_018 [situational]', '배포 후 프로덕션에서 버그가 발생했다면 어떤 순서로 대응하겠어요?

[AI Guide]
문제 인식 → 공유 → 해결 → 재발 방지의 흐름으로 서술하도록 유도한다.', 'data/behavioral-questions.json', '72d2b8201e7d5498221559af0708426180501f955808b5c32b92a63284490371', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_020 [standard]', '우리 회사와 이 직무에 지원하신 결정적인 동기는 무엇이며, 본인의 장기적인 커리어 목표와 어떻게 연결되나요?

[AI Guide]
지원자의 요약본에 나타난 과거 프로젝트의 도메인(예: 이커머스, 핀테크 등)을 언급하며, 왜 기존 도메인을 떠나 새로운 도전을 하려는지 물어볼 것.', 'data/behavioral-questions.json', '774e9f693f184f0dd756944a6314d76122f76c199fe1c27a9af0bea76cec700e', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_020 [pressure]', '단순히 회사의 네임밸류나 조건 때문이 아니라, 지원자님의 개인적인 기술적 갈증을 우리 회사에서 어떻게 해소할 수 있다고 생각하시나요?

[AI Guide]
지원자의 요약본에 나타난 과거 프로젝트의 도메인(예: 이커머스, 핀테크 등)을 언급하며, 왜 기존 도메인을 떠나 새로운 도전을 하려는지 물어볼 것.', 'data/behavioral-questions.json', 'ff55c42410336cfedf8227ecd59423db03294bc6f65b7c247d851ae16f2a5a11', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'B_020 [situational]', '저희 회사가 지금 겪고 있는 가장 큰 기술적 과제가 무엇이라고 생각하시나요? 본인이 합류한다면 그 과제를 어떻게 해결해 나갈 건가요?

[AI Guide]
지원자의 요약본에 나타난 과거 프로젝트의 도메인(예: 이커머스, 핀테크 등)을 언급하며, 왜 기존 도메인을 떠나 새로운 도전을 하려는지 물어볼 것.', 'data/behavioral-questions.json', '33cb21d0ae24f16d3054f55878ddebaa3391e7349b5505e15d4f4db8e8b3dbbf', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'C_001 [standard]', '프로젝트를 진행하며 팀원이나 매니저와 기술적인 설계 방향으로 크게 의견이 엇갈렸던 경험과, 이를 어떻게 조율했는지 말씀해 주세요.

[AI Guide]
지원자의 요약본 중 특정 아키텍처 패턴이나 라이브러리를 언급하며, 동료가 해당 기술 도입을 강하게 반대했던 상황을 가정하여 꼬리 질문을 던질 것.', 'data/behavioral-questions.json', '85a868e6acfe6f5b79ade31f2c6abf2ec637561b85bcca8c3a67dc14131ba536', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'C_001 [pressure]', '본인의 설계가 기술적으로 더 낫다고 100% 확신하는데 팀장님이 반대한다면 어떻게 설득하시겠습니까? 끝까지 굽히지 않은 적이 있나요?

[AI Guide]
지원자의 요약본 중 특정 아키텍처 패턴이나 라이브러리를 언급하며, 동료가 해당 기술 도입을 강하게 반대했던 상황을 가정하여 꼬리 질문을 던질 것.', 'data/behavioral-questions.json', 'da62c7494cb002639a16066a59418429ff5bc5ac57d579a0a9d9c9e9e64dd39a', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'C_001 [situational]', '개발 기한이 얼마 남지 않았는데, 동료가 갑자기 전체 아키텍처를 갈아엎자고 주장합니다. 감정적인 충돌 없이 어떻게 타협점을 찾으실 건가요?

[AI Guide]
지원자의 요약본 중 특정 아키텍처 패턴이나 라이브러리를 언급하며, 동료가 해당 기술 도입을 강하게 반대했던 상황을 가정하여 꼬리 질문을 던질 것.', 'data/behavioral-questions.json', 'a50fcbc0fa567da18fcef7c98c1937f590a20ec55ded6688f82d385a7a55b11b', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'C_002 [standard]', '코드 리뷰나 업무 평가에서 동료로부터 아주 뼈아픈 비판을 받았던 경험과, 이를 어떻게 수용하고 개선했는지 말씀해 주세요.

[AI Guide]
지원자가 작성한 코드 중 최적화가 덜 되었거나 복잡해 보이는 부분을 짚으며, ''이 코드를 동료가 강하게 비판했다면 어떻게 방어할 것인가?''라고 물어볼 것.', 'data/behavioral-questions.json', '70d283cca122f7fd07eb2a065a2b3e805149afdf8c8d6ecc374fde5622970162', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'C_002 [pressure]', '반대로 본인이 동료의 코드나 태도에 대해 아주 강한 비판을 전달해야 했던 적이 있나요? 상대방이 기분 상하지 않게 어떻게 전달하셨나요?

[AI Guide]
지원자가 작성한 코드 중 최적화가 덜 되었거나 복잡해 보이는 부분을 짚으며, ''이 코드를 동료가 강하게 비판했다면 어떻게 방어할 것인가?''라고 물어볼 것.', 'data/behavioral-questions.json', '9aac82182a8c05b57883b79b003d18675fb020ab36ba0fa889019905fe6cb6ca', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'C_002 [situational]', '본인이 밤새워 짠 코드를 시니어 개발자가 ''이건 도저히 프로덕션에 못 나가는 코드''라고 반려했습니다. 가장 먼저 어떤 행동을 취하시겠습니까?

[AI Guide]
지원자가 작성한 코드 중 최적화가 덜 되었거나 복잡해 보이는 부분을 짚으며, ''이 코드를 동료가 강하게 비판했다면 어떻게 방어할 것인가?''라고 물어볼 것.', 'data/behavioral-questions.json', '90f1e5891378fe9c4d25d00106793190dc7baef0e64aea3c4e16c585b8c8b624', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'C_003 [standard]', '개발 지식이 전혀 없는 기획자나 영업팀에게 복잡한 기술적 제약 사항을 설명하고 이해시켜야 했던 경험에 대해 말씀해 주세요.

[AI Guide]
요약본 내의 백엔드/프론트엔드 연동 부분이나 외부 API 사용 부분을 찾아내어, 해당 스펙을 PM이나 클라이언트에게 설명해야 하는 상황을 부여할 것.', 'data/behavioral-questions.json', 'f515a256aab204f14730c1418d05dd18a38accae0ce6bca3342e4009c5ad8cc5', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'C_003 [pressure]', '기획자가 3일 만에 말도 안 되는 기능을 만들어 달라고 요구합니다. ''기술적으로 안 됩니다''라는 말 말고, 어떻게 대안을 제시하며 설득하시겠습니까?

[AI Guide]
요약본 내의 백엔드/프론트엔드 연동 부분이나 외부 API 사용 부분을 찾아내어, 해당 스펙을 PM이나 클라이언트에게 설명해야 하는 상황을 부여할 것.', 'data/behavioral-questions.json', '888749f60ba5df2daa3a00db3715194c47f2ddcbf3b3239e20d2466ff36fad19', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'C_003 [situational]', '타 부서와의 협업 중, 우리 팀의 목표와 그쪽 팀의 KPI가 정면으로 충돌했습니다. 양쪽 모두 만족할 수 있는 해결책을 어떻게 도출해 내실 건가요?

[AI Guide]
요약본 내의 백엔드/프론트엔드 연동 부분이나 외부 API 사용 부분을 찾아내어, 해당 스펙을 PM이나 클라이언트에게 설명해야 하는 상황을 부여할 것.', 'data/behavioral-questions.json', 'bcfed02920e79c364fc64994c0f8d5100de05453a13136c26f01255fd525058e', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'T_001 [standard]', '팀 프로젝트에서 본인이 정확히 어떤 역할을 맡았고, 팀의 최종 결과물에 기여한 비중은 수치로 따지면 어느 정도인지 이유와 함께 말씀해 주세요.

[AI Guide]
요약본의 커밋 히스토리를 바탕으로, 지원자가 집중적으로 개발한 모듈이 팀 전체 파이프라인에서 어떤 핵심적인 영향을 미쳤는지 상세히 물어볼 것.', 'data/behavioral-questions.json', '8b957550a8bb6822a811d25117c495a0559ccc788cfa32d3abaeeaab84336440', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'T_001 [pressure]', '본인이 주로 돋보이는 역할을 맡는 편인가요, 아니면 남들이 꺼리는 궂은일을 맡는 편인가요? 실제 사례를 들어 증명해 보세요.

[AI Guide]
요약본의 커밋 히스토리를 바탕으로, 지원자가 집중적으로 개발한 모듈이 팀 전체 파이프라인에서 어떤 핵심적인 영향을 미쳤는지 상세히 물어볼 것.', 'data/behavioral-questions.json', 'e5d79eb99731ab8c8f147374ee215290f0763d30a49c076db4e6b2500fe31ca8', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'T_001 [situational]', '팀원 한 명이 잠수(연락 두절)를 타서 본인의 업무량이 두 배로 늘어났습니다. 불만을 표출하기 전에 가장 먼저 어떤 조치를 취하시겠습니까?

[AI Guide]
요약본의 커밋 히스토리를 바탕으로, 지원자가 집중적으로 개발한 모듈이 팀 전체 파이프라인에서 어떤 핵심적인 영향을 미쳤는지 상세히 물어볼 것.', 'data/behavioral-questions.json', '0569b758b6f7b586f708d77f3f5c8ef4e222da65e640d86f22c85d0755f98c2c', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'T_002 [standard]', '팀의 사기가 떨어졌거나 프로젝트가 위기에 처했을 때, 팀원들을 독려하고 성공적으로 이끌었던 리더십 경험이 있나요?

[AI Guide]
지원자가 팀 프로젝트의 브랜치 병합(Merge)이나 코드 컨벤션 설정을 주도했던 흔적이 있다면, 이를 바탕으로 어떻게 팀원들의 참여를 이끌어냈는지 질문할 것.', 'data/behavioral-questions.json', '8a148fa547f1e871b743692efc3bbc08fcff7d9b11e7c15122308175f068a4a4', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'T_002 [pressure]', '본인은 공식적인 팀장이 아니었음에도 주도적으로 나서서 팀을 이끌었던 적이 있나요? 자칫 월권으로 보일 수 있는데 어떻게 조율했나요?

[AI Guide]
지원자가 팀 프로젝트의 브랜치 병합(Merge)이나 코드 컨벤션 설정을 주도했던 흔적이 있다면, 이를 바탕으로 어떻게 팀원들의 참여를 이끌어냈는지 질문할 것.', 'data/behavioral-questions.json', 'b75ead9dc47045518b33fb6cfa9a571c9933b6b5231d525d3c6132e33ed4a845', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'T_002 [situational]', '협력해야 할 다른 팀이 우리 프로젝트 지원에 굉장히 소극적입니다. 그들의 동기를 유발해서 우리가 원하는 결과물을 얻어내려면 어떻게 해야 할까요?

[AI Guide]
지원자가 팀 프로젝트의 브랜치 병합(Merge)이나 코드 컨벤션 설정을 주도했던 흔적이 있다면, 이를 바탕으로 어떻게 팀원들의 참여를 이끌어냈는지 질문할 것.', 'data/behavioral-questions.json', 'fa20fd9cf737ac7e5b1de1a45b29accb5b51d8155cee9cb88091ebcc4f6672e1', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'P_001 [standard]', '지금까지 경험했던 가장 까다롭고 복잡했던 기술적 문제(또는 디버깅)는 무엇이었고, 어떤 논리적 단계를 거쳐 해결했나요?

[AI Guide]
지원자 코드 요약본 중 비동기 처리, DB 커넥션, 혹은 복잡한 상태 관리 로직을 짚으며, 그 부분에서 예상치 못한 병목이 발생했다고 가정하고 추적 과정을 물어볼 것.', 'data/behavioral-questions.json', '59204bc037e57a60e7b80eae7cc642032a4f5d54966df9f161d830da908af50e', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'P_001 [pressure]', '스택오버플로우나 구글링을 해도 답이 나오지 않는 런타임 에러를 마주했습니다. 본인만의 밑바닥(Deep Dive) 문제 해결 프로세스는 무엇인가요?

[AI Guide]
지원자 코드 요약본 중 비동기 처리, DB 커넥션, 혹은 복잡한 상태 관리 로직을 짚으며, 그 부분에서 예상치 못한 병목이 발생했다고 가정하고 추적 과정을 물어볼 것.', 'data/behavioral-questions.json', 'bb8c4e675547a1c28b5669b3ae8815e4d10785f9777d4dba0d4954fff06e27ac', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'P_001 [situational]', '배포를 완료했는데 특정 사용자 환경에서만 간헐적으로 메모리 누수가 발생합니다. 로그도 불분명한 상황에서 어떻게 원인을 추적하시겠습니까?

[AI Guide]
지원자 코드 요약본 중 비동기 처리, DB 커넥션, 혹은 복잡한 상태 관리 로직을 짚으며, 그 부분에서 예상치 못한 병목이 발생했다고 가정하고 추적 과정을 물어볼 것.', 'data/behavioral-questions.json', '7c218d347f3a8959344c87dbe32a6d49d4db2544826c9956f0e434a5a5844b05', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'P_002 [standard]', '누가 시키지 않았음에도, 비효율적인 레거시 코드나 프로세스를 발견하고 본인이 주도적으로 나서서 개선했던 경험을 말씀해 주세요.

[AI Guide]
지원자의 깃허브 리드미(README)나 CI/CD 설정 내역을 보고, 개발 생산성을 높이기 위해 본인이 자발적으로 구축한 자동화나 툴링 경험을 파고들 것.', 'data/behavioral-questions.json', '40cc73613274553f1f2c87137a13001871e47f129e4279b978b35fa776a0bef9', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'P_002 [pressure]', '주어진 요구사항(Requirement)을 구현하는 것 이상으로, 제품의 퀄리티나 유저 경험을 높이기 위해 본인의 시간을 과도하게 투자했던 적이 있나요? 왜 그렇게까지 했나요?

[AI Guide]
지원자의 깃허브 리드미(README)나 CI/CD 설정 내역을 보고, 개발 생산성을 높이기 위해 본인이 자발적으로 구축한 자동화나 툴링 경험을 파고들 것.', 'data/behavioral-questions.json', '8bb0b137bc63a0bea89eb243c8e6e061316ea15ecf976e4e23ba6f7e70b47e5e', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'P_002 [situational]', '회사에 입사해 보니 테스트 코드를 짜는 문화가 아예 없습니다. 바쁜 일정 속에서 팀원들에게 테스트 코드의 필요성을 어떻게 설득하고 도입할 건가요?

[AI Guide]
지원자의 깃허브 리드미(README)나 CI/CD 설정 내역을 보고, 개발 생산성을 높이기 위해 본인이 자발적으로 구축한 자동화나 툴링 경험을 파고들 것.', 'data/behavioral-questions.json', '30933b00a0a0d8b6f94e93ed3384e510b26420c283be532ad034566d113dc892', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'P_003 [standard]', '정답이 없거나 정보가 매우 부족한 상황에서 중요한 기술적 결정을 내려야 했던 경험이 있나요? 어떤 기준으로 판단을 내렸나요?

[AI Guide]
요약본에서 지원자가 특정 라이브러리를 사용한 부분을 짚으며, ''만약 당시 공식 문서가 빈약하고 레퍼런스가 없었다면 어떻게 도입 결정을 내렸을지'' 물어볼 것.', 'data/behavioral-questions.json', '9085eb7c50a4202c3d03914c5119c351521d8dff7bae92c6e0e69d80b19c978b', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'P_003 [pressure]', 'A라는 기술과 B라는 기술 중 하나를 골라야 하는데, 팀원들의 의견이 정확히 반으로 갈렸습니다. 본인이 최종 결정권자라면 무엇을 근거로 선택하겠습니까?

[AI Guide]
요약본에서 지원자가 특정 라이브러리를 사용한 부분을 짚으며, ''만약 당시 공식 문서가 빈약하고 레퍼런스가 없었다면 어떻게 도입 결정을 내렸을지'' 물어볼 것.', 'data/behavioral-questions.json', 'c91694f38e5eba861e2d193638d0c868bd5818ad5db3bdbf4047d3a886828496', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'P_003 [situational]', '새로운 프로젝트를 시작해야 하는데, 기획서가 너무 모호하고 요구사항이 텅 비어 있습니다. 개발자로서 이 불확실성을 어떻게 구체화해 나가실 건가요?

[AI Guide]
요약본에서 지원자가 특정 라이브러리를 사용한 부분을 짚으며, ''만약 당시 공식 문서가 빈약하고 레퍼런스가 없었다면 어떻게 도입 결정을 내렸을지'' 물어볼 것.', 'data/behavioral-questions.json', 'b4f04318474d7908bf41c29fc4b04423194ccf9e42bb75f9e215cae30e6ad57c', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'F_001 [standard]', '업무량이 감당할 수 없을 정도로 몰렸거나 마감이 아주 촉박했을 때, 본인만의 우선순위 설정 기준과 대처 방법은 무엇인가요?

[AI Guide]
지원자의 코드 중 ''시간이 부족해서 대충 짠 흔적(Technical Debt)''이 보이는 부분을 정확히 타겟팅하여, 일정 압박 시 품질과 속도 중 무엇을 포기했는지 물어볼 것.', 'data/behavioral-questions.json', '9d71e32d314b136e8480649677cf4a7a913e0426f85f93539bad6f97578160f5', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'F_001 [pressure]', '일정 산정에 철저히 실패해서 팀의 데드라인을 어겼던 경험이 있나요? 변명 없이, 왜 그런 실수를 했고 어떻게 책임졌는지 말씀해 주세요.

[AI Guide]
지원자의 코드 중 ''시간이 부족해서 대충 짠 흔적(Technical Debt)''이 보이는 부분을 정확히 타겟팅하여, 일정 압박 시 품질과 속도 중 무엇을 포기했는지 물어볼 것.', 'data/behavioral-questions.json', '096e3c89c35de4f63b9f73c2d9bc86b092ed220b17eba4f45746985cd86a9e4e', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'F_001 [situational]', '장기적인 아키텍처 개선 작업을 하고 있는데, 갑자기 경영진이 내일까지 긴급 핫픽스를 처리하라고 지시합니다. 두 가지 업무를 어떻게 병행하시겠습니까?

[AI Guide]
지원자의 코드 중 ''시간이 부족해서 대충 짠 흔적(Technical Debt)''이 보이는 부분을 정확히 타겟팅하여, 일정 압박 시 품질과 속도 중 무엇을 포기했는지 물어볼 것.', 'data/behavioral-questions.json', 'a87d28bd3a7c7efab8313ff1d15ead897ce76914541350a6fa2d45a9df8cc157', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'F_002 [standard]', '프로젝트를 진행하며 크나큰 실패를 겪었거나 운영 서버에 장애를 냈던 경험이 있나요? 그 과정에서 배운 가장 큰 교훈은 무엇인가요?

[AI Guide]
요약본의 리팩토링 커밋이나 버그 픽스 내역을 기반으로, 최초에 잘못 설계해서 큰 장애가 날 뻔했던 상황을 회고하게 유도할 것.', 'data/behavioral-questions.json', '0779176f2a0e9658b083a185a0b7a5123b51d57f529f1d0add492bd28e474484', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'F_002 [pressure]', '본인이 과거에 내렸던 기술적 결정 중, 지금 생각하면 너무 부끄럽거나 후회되는 설계가 있나요? 과거로 돌아간다면 어떻게 고치고 싶나요?

[AI Guide]
요약본의 리팩토링 커밋이나 버그 픽스 내역을 기반으로, 최초에 잘못 설계해서 큰 장애가 날 뻔했던 상황을 회고하게 유도할 것.', 'data/behavioral-questions.json', '07b7f2721db73207263240874da9876ef922357633fc4e7ffddd87112f1ea1fc', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'F_002 [situational]', '금요일 퇴근 직전, 본인이 작성한 쿼리 실수로 프로덕션 DB의 주요 데이터가 날아갔습니다. 식은땀이 나는 이 상황에서 앞으로 1시간 동안의 행동 요령을 읊어보세요.

[AI Guide]
요약본의 리팩토링 커밋이나 버그 픽스 내역을 기반으로, 최초에 잘못 설계해서 큰 장애가 날 뻔했던 상황을 회고하게 유도할 것.', 'data/behavioral-questions.json', '2ff19fa9ecc713701695c32c99461812507ba4b3e33ea048ca2dfd51270e3777', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'T_003 [standard]', '단순히 기능이 돌아가는 것을 넘어, 코드의 품질과 유지보수성을 높이기 위해 개발 과정에서 어떤 구체적인 원칙을 지키시나요?

[AI Guide]
지원자 코드 요약본의 전반적인 구조(모듈화, 변수명, 주석 등)를 평가하며, 만약 이 코드를 오픈소스로 공개한다면 어떤 점이 가장 부끄러울지 압박할 것.', 'data/behavioral-questions.json', 'c283181107b4689cbd38cda424e81ed8c65faa0da503925ac79440596f5cf4bb', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'T_003 [pressure]', '빠르게 배포하라는 압박이 들어와도, 본인만의 ''이것만은 절대 포기할 수 없다''는 최소한의 코드 품질 마지노선이 있나요? 있다면 무엇인가요?

[AI Guide]
지원자 코드 요약본의 전반적인 구조(모듈화, 변수명, 주석 등)를 평가하며, 만약 이 코드를 오픈소스로 공개한다면 어떤 점이 가장 부끄러울지 압박할 것.', 'data/behavioral-questions.json', '2d4bc333474234100f5bae9536ca617f3ca011490baa4f68e1a09528fd1f0bfd', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'T_003 [situational]', '전임자가 남기고 간 스파게티 코드를 인수인계받았습니다. 당장 새 기능도 추가해야 하는데, 이 기술 부채(Technical Debt)를 어떻게 처리하시겠습니까?

[AI Guide]
지원자 코드 요약본의 전반적인 구조(모듈화, 변수명, 주석 등)를 평가하며, 만약 이 코드를 오픈소스로 공개한다면 어떤 점이 가장 부끄러울지 압박할 것.', 'data/behavioral-questions.json', 'a61d80f006407cb081eb44b1ab7a5eef77fe77d8997d66004da45e5c4ba11c15', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'T_004 [standard]', '빠르게 변하는 IT 트렌드 속에서 본인만의 학습 노하우는 무엇인가요? 최근에 새롭게 공부해서 프로젝트에 적용해 본 기술이 있다면 소개해주세요.

[AI Guide]
요약본 내에서 지원자가 사용한 가장 최신 기술이나 난이도 높은 라이브러리를 찾아내어, 이것의 내부 동작 원리까지 깊게 파고들어(Deep Dive) 학습했는지 검증할 것.', 'data/behavioral-questions.json', '04111a30cc752c3a1c91f59f1012904085673039a51a180554118cc1f31fc734', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'T_004 [pressure]', '단순히 공식 문서를 읽거나 강의를 듣는 수동적인 학습 말고, 본인이 학습한 내용을 동료들에게 전파하여 조직 전체의 성장을 이끌어낸 사례가 있나요?

[AI Guide]
요약본 내에서 지원자가 사용한 가장 최신 기술이나 난이도 높은 라이브러리를 찾아내어, 이것의 내부 동작 원리까지 깊게 파고들어(Deep Dive) 학습했는지 검증할 것.', 'data/behavioral-questions.json', '360d4ce6aa2020b0e92a575f5bcf0a549bdd0fe86ab31079b17ffeb89f25814b', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;
INSERT INTO knowledge_items (source_key, title, content, file_path, content_hash, created_at, updated_at) VALUES ('local-behavioral', 'T_004 [situational]', '내일부터 당장 본인이 한 번도 써본 적 없는 언어(예: Go, Rust)로 핵심 마이크로서비스를 개발해야 합니다. 첫 일주일 동안의 학습 계획을 세워보세요.

[AI Guide]
요약본 내에서 지원자가 사용한 가장 최신 기술이나 난이도 높은 라이브러리를 찾아내어, 이것의 내부 동작 원리까지 깊게 파고들어(Deep Dive) 학습했는지 검증할 것.', 'data/behavioral-questions.json', '12fade40bcf5226183b91930182b0ce1cf3572cae9646d768cb998cb6afdc093', current_timestamp, current_timestamp) ON CONFLICT ON CONSTRAINT uk_knowledge_items DO NOTHING;

-- behavioral 태그 연결
INSERT INTO knowledge_item_tags (knowledge_item_id, knowledge_tag_id)
SELECT ki.id, kt.id FROM knowledge_items ki CROSS JOIN knowledge_tags kt
WHERE ki.source_key = 'local-behavioral' AND kt.name = 'behavioral'
AND NOT EXISTS (SELECT 1 FROM knowledge_item_tags kit WHERE kit.knowledge_item_id = ki.id AND kit.knowledge_tag_id = kt.id);
