import { useEffect, useMemo, useState } from 'react';
import { 
  App as AntdApp, 
  Avatar, 
  Button, 
  Card, 
  DatePicker, 
  Image, 
  Input, 
  Modal, 
  Select, 
  Space, 
  Table, 
  Tag, 
  Tooltip,
  Typography,
  Popconfirm,
  Badge
} from 'antd';
import { 
  DeleteOutlined, 
  EyeOutlined, 
  SearchOutlined,
  FilterOutlined,
  ExportOutlined,
  FileTextOutlined,
  PictureOutlined,
  UserOutlined,
  HeartOutlined,
  CommentOutlined,
  ShareAltOutlined,
  StopOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import apiClient from '../api/client';
import { API_BASE_URL } from '../config';

const { RangePicker } = DatePicker;
const { Text, Paragraph } = Typography;
const { TextArea } = Input;

type PostItem = {
  _id: string;
  content: string;
  images?: string[];
  userId: {
    _id: string;
    username: string;
    avatar?: string;
    profile?: {
      firstName?: string;
      lastName?: string;
    };
  };
  likes: Array<{ user: string; _id: string }>;
  comments: Array<any>;
  shares: Array<{ user: string; _id: string }>;
  privacySetting: 'public' | 'friends' | 'only_me';
  location?: string;
  createdAt: string;
  updatedAt: string;
  isActive?: boolean;
  isDeleted?: boolean;
};

type PostsResponse = {
  success: boolean;
  data: {
    posts: PostItem[];
    pagination: { page: number; limit: number; total: number; pages: number };
  };
};

export default function Posts() {
  const { modal, message: messageApi } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [data, setData] = useState<PostItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [filters, setFilters] = useState({
    onlyFriends: false,
    mediaOnly: false,
    hashtagOnly: false,
    dateRange: null as [dayjs.Dayjs, dayjs.Dayjs] | null,
  });
  const [viewModal, setViewModal] = useState<PostItem | null>(null);
  const [deleteModal, setDeleteModal] = useState<PostItem | null>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  const resolveAvatarUrl = (avatar?: string) => {
    if (!avatar) return undefined;
    if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar;
    const base = API_BASE_URL.replace(/\/$/, '');
    return `${base}${avatar}`;
  };

  const resolveImageUrl = (image?: string) => {
    if (!image) return undefined;
    if (image.startsWith('http://') || image.startsWith('https://')) return image;
    const base = API_BASE_URL.replace(/\/$/, '');
    return `${base}${image}`;
  };

  const columns = useMemo(
    () => [
      {
        title: 'ID',
        dataIndex: '_id',
        key: '_id',
        width: 100,
        render: (id: string) => (
          <Tooltip title={id}>
            <Text code style={{ fontSize: '11px' }}>
              {id.substring(0, 8)}...
            </Text>
          </Tooltip>
        ),
      },
      {
        title: 'Author',
        key: 'author',
        width: 150,
        render: (_: unknown, record: PostItem) => (
          <Space>
            <Avatar 
              size={32} 
              src={resolveAvatarUrl(record.userId?.avatar)} 
              icon={<UserOutlined />}
            />
            <div>
              <div style={{ fontWeight: 500 }}>
                {record.userId?.username || 'Unknown'}
              </div>
              <Text type="secondary" style={{ fontSize: '11px' }}>
                {record.userId?.profile?.firstName} {record.userId?.profile?.lastName}
              </Text>
            </div>
          </Space>
        ),
      },
      {
        title: 'Content',
        dataIndex: 'content',
        key: 'content',
        ellipsis: true,
        render: (content: string) => (
          <Paragraph 
            ellipsis={{ rows: 2, expandable: false }} 
            style={{ margin: 0, maxWidth: 300 }}
          >
            {content || <Text type="secondary">No content</Text>}
          </Paragraph>
        ),
      },
      {
        title: 'Media',
        key: 'media',
        width: 80,
        render: (_: unknown, record: PostItem) => (
          record.images && record.images.length > 0 ? (
            <Badge count={record.images.length}>
              <PictureOutlined style={{ fontSize: 20 }} />
            </Badge>
          ) : (
            <Text type="secondary">-</Text>
          )
        ),
      },
      {
        title: 'Engagement',
        key: 'engagement',
        width: 150,
        render: (_: unknown, record: PostItem) => (
          <Space size="small">
            <Tooltip title="Likes">
              <Tag icon={<HeartOutlined />} color="red">
                {record.likes?.length || 0}
              </Tag>
            </Tooltip>
            <Tooltip title="Comments">
              <Tag icon={<CommentOutlined />} color="blue">
                {record.comments?.length || 0}
              </Tag>
            </Tooltip>
            <Tooltip title="Shares">
              <Tag icon={<ShareAltOutlined />} color="green">
                {record.shares?.length || 0}
              </Tag>
            </Tooltip>
          </Space>
        ),
      },
      {
        title: 'Privacy',
        dataIndex: 'privacySetting',
        key: 'privacySetting',
        width: 100,
        render: (setting: string) => (
          <Tag color={
            setting === 'public' ? 'green' : 
            setting === 'friends' ? 'blue' : 
            'default'
          }>
            {setting}
          </Tag>
        ),
      },
      {
        title: 'Location',
        dataIndex: 'location',
        key: 'location',
        width: 120,
        render: (location?: string) => location || <Text type="secondary">-</Text>,
      },
      {
        title: 'Created',
        dataIndex: 'createdAt',
        key: 'createdAt',
        width: 150,
        render: (date: string) => dayjs(date).format('DD/MM/YYYY HH:mm'),
        sorter: (a: PostItem, b: PostItem) => 
          dayjs(a.createdAt).unix() - dayjs(b.createdAt).unix(),
      },
    ],
    []
  );

  const fetchPosts = async (p = page, size = pageSize, query = searchQuery) => {
    setLoading(true);
    try {
      if (query && query.trim().length >= 2) {
        // Use search endpoint
        const params: any = {
          q: query,
          page: p,
          limit: size,
        };
        
        if (filters.onlyFriends) params.onlyFriends = true;
        if (filters.mediaOnly) params.mediaOnly = true;
        if (filters.hashtagOnly) params.hashtagOnly = true;
        if (filters.dateRange) {
          params.dateFrom = filters.dateRange[0].toISOString();
          params.dateTo = filters.dateRange[1].toISOString();
        }

        const { data } = await apiClient.get<PostsResponse>('/posts/search', { params });
        setData(data.data?.posts || data.data || []);
        setTotal(data.data?.pagination?.total || 0);
      } else {
        // Use feed endpoint
        const { data } = await apiClient.get<PostsResponse>('/posts/feed', {
          params: { page: p, limit: size },
        });
        setData(data.data?.posts || data.data || []);
        setTotal(data.data?.pagination?.total || 0);
      }
      setPage(p);
      setPageSize(size);
    } catch (err: any) {
      messageApi.error(err?.response?.data?.message || 'Failed to fetch posts');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPosts(1, pageSize, searchQuery);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters]);

  const handleDelete = async (post: PostItem) => {
    try {
      await apiClient.delete(`/posts/${post._id}`);
      messageApi.success('Post deleted successfully');
      setDeleteModal(null);
      fetchPosts(page, pageSize, searchQuery);
    } catch (err: any) {
      messageApi.error(err?.response?.data?.message || 'Failed to delete post');
    }
  };

  const handleBulkDelete = async () => {
    if (selectedRowKeys.length === 0) {
      messageApi.warning('Please select posts to delete');
      return;
    }
    
    modal.confirm({
      title: `Delete ${selectedRowKeys.length} post(s)?`,
      content: 'This action cannot be undone.',
      onOk: async () => {
        try {
          const deletePromises = selectedRowKeys.map(id => 
            apiClient.delete(`/posts/${id}`)
          );
          await Promise.all(deletePromises);
          messageApi.success(`${selectedRowKeys.length} post(s) deleted successfully`);
          setSelectedRowKeys([]);
          fetchPosts(page, pageSize, searchQuery);
        } catch (err: any) {
          messageApi.error('Failed to delete some posts');
        }
      },
    });
  };

  const handleBulkHide = async () => {
    if (selectedRowKeys.length === 0) {
      messageApi.warning('Please select posts to hide');
      return;
    }
    
    modal.confirm({
      title: `Hide ${selectedRowKeys.length} post(s)?`,
      content: 'These posts will be hidden from feeds.',
      onOk: async () => {
        try {
          const hidePromises = selectedRowKeys.map(id => 
            apiClient.post(`/posts/${id}/hide`)
          );
          await Promise.all(hidePromises);
          messageApi.success(`${selectedRowKeys.length} post(s) hidden successfully`);
          setSelectedRowKeys([]);
          fetchPosts(page, pageSize, searchQuery);
        } catch (err: any) {
          messageApi.error('Failed to hide some posts');
        }
      },
    });
  };

  const handleExport = () => {
    const csv = [
      ['ID', 'Author', 'Content', 'Likes', 'Comments', 'Shares', 'Privacy', 'Location', 'Created At'].join(','),
      ...data.map(post => [
        post._id,
        post.userId?.username || '',
        `"${(post.content || '').replace(/"/g, '""')}"`,
        post.likes?.length || 0,
        post.comments?.length || 0,
        post.shares?.length || 0,
        post.privacySetting,
        post.location || '',
        dayjs(post.createdAt).format('YYYY-MM-DD HH:mm:ss')
      ].join(','))
    ].join('\n');

    const blob = new Blob([csv], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `posts_${dayjs().format('YYYY-MM-DD_HH-mm-ss')}.csv`;
    a.click();
    window.URL.revokeObjectURL(url);
    messageApi.success('Posts exported successfully');
  };

  return (
    <Card 
      title={
        <Space>
          <FileTextOutlined />
          <span>Posts Management</span>
        </Space>
      }
      extra={
        <Space>
          <Input.Search
            placeholder="Search posts..."
            allowClear
            style={{ width: 300 }}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onSearch={(val) => fetchPosts(1, pageSize, val)}
            enterButton={<SearchOutlined />}
          />
          <Button 
            icon={<FilterOutlined />}
            onClick={() => {
              modal.info({
                title: 'Filters',
                content: (
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Select
                      style={{ width: '100%' }}
                      placeholder="Filter by type"
                      value={filters.mediaOnly ? 'media' : filters.hashtagOnly ? 'hashtag' : filters.onlyFriends ? 'friends' : undefined}
                      onChange={(val) => {
                        setFilters(prev => ({
                          ...prev,
                          mediaOnly: val === 'media',
                          hashtagOnly: val === 'hashtag',
                          onlyFriends: val === 'friends',
                        }));
                      }}
                      allowClear
                    >
                      <Select.Option value="friends">Friends Only</Select.Option>
                      <Select.Option value="media">Media Only</Select.Option>
                      <Select.Option value="hashtag">Hashtags Only</Select.Option>
                    </Select>
                    <RangePicker
                      style={{ width: '100%' }}
                      value={filters.dateRange}
                      onChange={(dates) => {
                        setFilters(prev => ({
                          ...prev,
                          dateRange: dates as [dayjs.Dayjs, dayjs.Dayjs] | null,
                        }));
                      }}
                    />
                  </Space>
                ),
              });
            }}
          >
            Filters
          </Button>
          <Button icon={<ExportOutlined />} onClick={handleExport}>
            Export
          </Button>
          {selectedRowKeys.length > 0 && (
            <Space>
              <Badge count={selectedRowKeys.length}>
                <Button>Selected</Button>
              </Badge>
              <Button 
                danger 
                icon={<DeleteOutlined />}
                onClick={handleBulkDelete}
              >
                Delete ({selectedRowKeys.length})
              </Button>
              <Button 
                icon={<StopOutlined />}
                onClick={handleBulkHide}
              >
                Hide ({selectedRowKeys.length})
              </Button>
            </Space>
          )}
        </Space>
      }
    >
      <Table
        rowKey="_id"
        loading={loading}
        rowSelection={{
          selectedRowKeys,
          onChange: setSelectedRowKeys,
        }}
        columns={[
          ...columns as any,
          {
            title: 'Actions',
            key: 'actions',
            width: 120,
            fixed: 'right',
            render: (_: unknown, record: PostItem) => (
              <Space>
                <Tooltip title="View Details">
                  <Button 
                    size="small" 
                    icon={<EyeOutlined />}
                    onClick={() => setViewModal(record)}
                  />
                </Tooltip>
                <Popconfirm
                  title="Delete this post?"
                  description="This action cannot be undone."
                  onConfirm={() => handleDelete(record)}
                  okText="Yes"
                  cancelText="No"
                >
                  <Tooltip title="Delete">
                    <Button 
                      size="small" 
                      danger 
                      icon={<DeleteOutlined />}
                    />
                  </Tooltip>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
        dataSource={data}
        pagination={{
          total,
          current: page,
          pageSize,
          showSizeChanger: true,
          showTotal: (total) => `Total ${total} posts`,
          onChange: (p, s) => fetchPosts(p, s, searchQuery),
        }}
        scroll={{ x: 1500 }}
      />

      {/* View Post Modal */}
      <Modal
        title="Post Details"
        open={!!viewModal}
        onCancel={() => setViewModal(null)}
        footer={[
          <Button key="close" onClick={() => setViewModal(null)}>
            Close
          </Button>,
          <Popconfirm
            key="delete"
            title="Delete this post?"
            onConfirm={() => viewModal && handleDelete(viewModal)}
          >
            <Button danger>Delete Post</Button>
          </Popconfirm>,
        ]}
        width={800}
      >
        {viewModal && (
          <Space direction="vertical" style={{ width: '100%' }} size="large">
            <div>
              <Text strong>Author: </Text>
              <Space>
                <Avatar src={resolveAvatarUrl(viewModal.userId?.avatar)} />
                <Text>{viewModal.userId?.username}</Text>
              </Space>
            </div>
            <div>
              <Text strong>Content:</Text>
              <Paragraph>{viewModal.content || 'No content'}</Paragraph>
            </div>
            {viewModal.images && viewModal.images.length > 0 && (
              <div>
                <Text strong>Images ({viewModal.images.length}):</Text>
                <div style={{ marginTop: 8 }}>
                  <Image.PreviewGroup>
                    {viewModal.images.map((img, idx) => (
                      <Image
                        key={idx}
                        width={100}
                        height={100}
                        src={resolveImageUrl(img)}
                        style={{ marginRight: 8, marginBottom: 8, objectFit: 'cover' }}
                      />
                    ))}
                  </Image.PreviewGroup>
                </div>
              </div>
            )}
            <div>
              <Space>
                <Tag icon={<HeartOutlined />} color="red">
                  {viewModal.likes?.length || 0} Likes
                </Tag>
                <Tag icon={<CommentOutlined />} color="blue">
                  {viewModal.comments?.length || 0} Comments
                </Tag>
                <Tag icon={<ShareAltOutlined />} color="green">
                  {viewModal.shares?.length || 0} Shares
                </Tag>
                <Tag>{viewModal.privacySetting}</Tag>
              </Space>
            </div>
            {viewModal.location && (
              <div>
                <Text strong>Location: </Text>
                <Text>{viewModal.location}</Text>
              </div>
            )}
            <div>
              <Text strong>Created: </Text>
              <Text>{dayjs(viewModal.createdAt).format('DD/MM/YYYY HH:mm:ss')}</Text>
            </div>
            <div>
              <Text strong>Updated: </Text>
              <Text>{dayjs(viewModal.updatedAt).format('DD/MM/YYYY HH:mm:ss')}</Text>
            </div>
          </Space>
        )}
      </Modal>
    </Card>
  );
}

