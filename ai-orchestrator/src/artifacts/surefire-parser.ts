import { XMLParser } from "fast-xml-parser";

export interface SurefireFailure {
  className: string;
  testName: string;
  message: string;
  type: string;
}

export interface SurefireReport {
  totalTests: number;
  failures: SurefireFailure[];
}

const parser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: "",
  textNodeName: "text"
});

export function parseSurefireReport(xml: string): SurefireReport {
  const parsed = parser.parse(xml) as {
    testsuite?: {
      tests?: string;
      testcase?: unknown;
    };
  };

  const suite = parsed.testsuite;
  if (!suite) {
    return { totalTests: 0, failures: [] };
  }

  const testCases = Array.isArray(suite.testcase)
    ? suite.testcase
    : suite.testcase
      ? [suite.testcase]
      : [];

  const failures = testCases.flatMap((testCase) => {
    const item = testCase as {
      classname?: string;
      name?: string;
      failure?: {
        message?: string;
        type?: string;
      };
    };

    if (!item.failure) {
      return [];
    }

    return [
      {
        className: item.classname ?? "",
        testName: item.name ?? "",
        message: item.failure.message ?? "",
        type: item.failure.type ?? ""
      }
    ];
  });

  return {
    totalTests: Number(suite.tests ?? testCases.length),
    failures
  };
}
