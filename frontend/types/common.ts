// 백엔드 ApiMeta.Pagination과 1:1 대응
export interface Pagination {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}