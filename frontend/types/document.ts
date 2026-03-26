// 백엔드 DocumentResponse와 1:1 대응
export type DocumentType = 'resume' | 'award' | 'certificate' | 'other';
export type ExtractStatus = 'pending' | 'success' | 'failed';

export interface Document {
  id: number;
  documentType: DocumentType;
  originalFileName: string;
  mimeType: string;
  fileSizeBytes: number;
  extractStatus: ExtractStatus;
  uploadedAt: string;
  extractedAt: string | null;
  extractedText: string | null;
}
